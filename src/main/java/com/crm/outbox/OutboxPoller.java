package com.crm.outbox;

import com.crm.kafka.config.KafkaTopics;
import com.crm.kafka.message.DealStatusChangedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Poller для Outbox-таблицы — читает PENDING-записи и публикует в Kafka.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * АРХИТЕКТУРА
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Поток событий:
 *   [DealService.changeStatus()]
 *     └─ INSERT outbox_messages (status=PENDING)   ← в той же транзакции что UPDATE deals
 *
 *   [OutboxPoller, каждую секунду]
 *     └─ SELECT TOP 100 WHERE status='PENDING'     ← отдельная транзакция, вне бизнес-логики
 *     └─ deserialize payload → DTO                 ← восстанавливаем тип для JsonSerializer
 *     └─ KafkaTemplate.send(dto)                   ← __TypeId__ = DealStatusChangedMessage
 *     └─ UPDATE status='SENT'                      ← через outboxRepository.save() (отдельная TX)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ПОЧЕМУ НЕ ОТПРАВЛЯЕМ payload (String) НАПРЯМУЮ
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  kafkaTemplate использует JsonSerializer. При отправке String он добавляет
 *  заголовок __TypeId__=java.lang.String. DealEventConsumer ожидает
 *  __TypeId__=DealStatusChangedMessage — при несовпадении JsonDeserializer
 *  вернёт LinkedHashMap вместо DTO → ClassCastException в consumer'е.
 *
 *  Решение: десериализовать JSON payload → DTO-объект → передать в kafkaTemplate.
 *  JsonSerializer установит правильный __TypeId__ и consumer успешно
 *  десериализует сообщение.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * @SCHEDULED ПАРАМЕТРЫ
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  fixedDelay=1000ms:
 *   Пауза ПОСЛЕ завершения предыдущего вызова, а не МЕЖДУ началами.
 *   Если обработка 100 сообщений занимает 500ms → следующий запуск через 1500ms.
 *   Prevents overlapping: при задержке Kafka poller не накапливает параллельные вызовы.
 *
 *  fixedRate — альтернатива:
 *   Запуск каждые N мс независимо от времени выполнения.
 *   Риск: параллельные экземпляры poller'а конкурируют за одни записи.
 *   Для outbox fixedDelay предпочтительнее.
 *
 *  initialDelay=5000ms:
 *   Ждать 5 секунд после старта приложения.
 *   Даёт время Kafka-соединению установиться перед первым poll'ом.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ТРАНЗАКЦИОННОСТЬ и SELF-INVOCATION
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  poll() НЕ помечен @Transactional намеренно:
 *   Kafka-публикация и обновление статуса — два разных side-effect.
 *   Если обернуть всё в одну транзакцию:
 *    - Транзакция держится открытой во время send() → lock на строках outbox
 *    - При медленной Kafka или network latency → долгие транзакции, deadlocks
 *
 *  markAsSent/markAsFailed НЕ помечены @Transactional:
 *   Они вызываются из whenComplete-callback (другой поток) и catch-блока.
 *   Оба случая — self-invocation через this: Spring AOP proxy не перехватывает вызов.
 *   Поэтому @Transactional на методах OutboxPoller не работает при вызове изнутри класса.
 *
 *   Решение: транзакция обеспечивается SimpleJpaRepository.save() который
 *   аннотирован @Transactional сам по себе. Каждый save — отдельная мини-TX.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ГАРАНТИЯ AT-LEAST-ONCE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Если poller упадёт после send() но до markAsSent():
 *   - Запись остаётся PENDING
 *   - При следующем тике сообщение будет отправлено повторно
 *   - Consumer обработает дубликат — но он идемпотентен (Redis SET NX в DealEventConsumer)
 *
 *  Это сознательный компромисс: at-least-once лучше at-most-once для бизнес-событий.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * МАРШРУТИЗАЦИЯ В ТОПИК
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  aggregateType → имя топика и тип DTO без if-else через switch expression.
 *  При добавлении нового типа агрегата:
 *   1. Добавить константу в KafkaTopics
 *   2. Добавить case в resolveTopicName() и deserializePayload()
 *  Не нужно менять остальной код поллера — open/closed principle.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPoller(OutboxRepository outboxRepository,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Основной цикл поллера.
     *
     * fixedDelay: следующий запуск через 1с ПОСЛЕ окончания текущего.
     * initialDelay: не стартовать сразу — дать время Kafka-соединению.
     */
    @Scheduled(fixedDelay = 1_000, initialDelay = 5_000)
    public void poll() {
        List<OutboxMessage> pending = outboxRepository
                .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pending.isEmpty()) {
            return;
        }

        log.debug("OutboxPoller: found {} PENDING messages", pending.size());

        for (OutboxMessage message : pending) {
            try {
                String topic = resolveTopicName(message.getAggregateType());

                // Десериализуем JSON payload обратно в DTO:
                //  Если передать String напрямую, JsonSerializer установит
                //  __TypeId__=String → consumer получит LinkedHashMap вместо DTO.
                Object dto = deserializePayload(message.getAggregateType(), message.getPayload());

                // aggregateId как partition key → ordering событий по конкретному агрегату
                kafkaTemplate.send(topic, message.getAggregateId(), dto)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                // KafkaTemplate.send() завершился ошибкой асинхронно
                                markAsFailed(message);
                                log.error("OutboxPoller: failed to send messageId={} aggregateId={}",
                                        message.getMessageId(), message.getAggregateId(), ex);
                            } else {
                                markAsSent(message);
                                log.debug("OutboxPoller: sent messageId={} → topic={} partition={} offset={}",
                                        message.getMessageId(), topic,
                                        result.getRecordMetadata().partition(),
                                        result.getRecordMetadata().offset());
                            }
                        });
            } catch (Exception e) {
                // Синхронная ошибка (напр., topic не существует или ошибка десериализации)
                markAsFailed(message);
                log.error("OutboxPoller: exception for messageId={}", message.getMessageId(), e);
            }
        }
    }

    /**
     * Помечает сообщение как успешно отправленное в Kafka.
     *
     * Вызывается из whenComplete-callback (другой поток).
     * НЕ @Transactional: self-invocation через this обходит Spring proxy.
     * Транзакция обеспечивается SimpleJpaRepository.save() (@Transactional внутри).
     */
    public void markAsSent(OutboxMessage message) {
        message.setStatus(OutboxStatus.SENT);
        message.setProcessedAt(Instant.now());
        outboxRepository.save(message);
    }

    /**
     * Помечает сообщение как неудавшееся.
     * FAILED-записи можно повторить вручную: UPDATE status='PENDING'.
     * Мониторинг: COUNT WHERE status='FAILED' > 0 → алерт.
     */
    public void markAsFailed(OutboxMessage message) {
        message.setStatus(OutboxStatus.FAILED);
        message.setProcessedAt(Instant.now());
        outboxRepository.save(message);
    }

    /**
     * Определяет Kafka-топик по типу агрегата.
     */
    private String resolveTopicName(String aggregateType) {
        return switch (aggregateType) {
            case "Deal" -> KafkaTopics.DEAL_STATUS_CHANGED;
            default -> throw new IllegalArgumentException(
                    "Unknown aggregateType for outbox routing: " + aggregateType);
        };
    }

    /**
     * Десериализует JSON-payload обратно в DTO по типу агрегата.
     *
     * Необходимо потому что kafkaTemplate использует JsonSerializer,
     * который записывает заголовок __TypeId__ = полное имя класса.
     * Consumer с JsonDeserializer читает __TypeId__ чтобы определить целевой тип.
     * Без этого consumer получит LinkedHashMap вместо нужного DTO.
     */
    private Object deserializePayload(String aggregateType, String payload) {
        Class<?> targetClass = switch (aggregateType) {
            case "Deal" -> DealStatusChangedMessage.class;
            default -> throw new IllegalArgumentException(
                    "Unknown aggregateType for payload deserialization: " + aggregateType);
        };
        try {
            return objectMapper.readValue(payload, targetClass);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Cannot deserialize outbox payload for aggregateType=" + aggregateType, e);
        }
    }
}
