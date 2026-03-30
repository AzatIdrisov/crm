package com.crm.outbox;

import com.crm.kafka.config.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 *     └─ KafkaTemplate.send()                      ← после коммита SELECT-транзакции
 *     └─ UPDATE status='SENT'                      ← в отдельной транзакции
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
 * ТРАНЗАКЦИОННОСТЬ
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  poll() НЕ помечен @Transactional намеренно:
 *   Kafka-публикация и обновление статуса — два разных side-effect.
 *   Если обернуть всё в одну транзакцию:
 *    - Транзакция держится открытой во время send() → lock на строках outbox
 *    - При медленной Kafka или network latency → долгие транзакции, deadlocks
 *
 *  Вместо этого — два отдельных шага:
 *   1. Читаем PENDING (read-only транзакция через @Transactional(readOnly=true) в репозитории)
 *   2. Для каждого: send() → markAsSent()/markAsFailed() в отдельной транзакции
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
 *  aggregateType → имя топика без if-else через switch expression.
 *  При добавлении нового типа агрегата:
 *   1. Добавить константу в KafkaTopics
 *   2. Добавить case в resolveTopicName()
 *  Не нужно менять поллер — open/closed principle.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxPoller(OutboxRepository outboxRepository,
                        KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
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

                // aggregateId как partition key → ordering событий по конкретному агрегату
                kafkaTemplate.send(topic, message.getAggregateId(), message.getPayload())
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
                // Синхронная ошибка (напр., topic не существует)
                markAsFailed(message);
                log.error("OutboxPoller: exception for messageId={}", message.getMessageId(), e);
            }
        }
    }

    /**
     * Помечает сообщение как успешно отправленное в Kafka.
     * Каждое обновление — отдельная мини-транзакция (нет риска долгого lock'а).
     */
    @Transactional
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
    @Transactional
    public void markAsFailed(OutboxMessage message) {
        message.setStatus(OutboxStatus.FAILED);
        message.setProcessedAt(Instant.now());
        outboxRepository.save(message);
    }

    /**
     * Определяет Kafka-топик по типу агрегата.
     *
     * aggregateType хранится в outbox_messages как строка ("Deal", "Customer").
     * Это позволяет не хардкодить маппинг в бизнес-слое — DealService
     * просто передаёт "Deal", не зная о Kafka-топиках.
     */
    private String resolveTopicName(String aggregateType) {
        return switch (aggregateType) {
            case "Deal" -> KafkaTopics.DEAL_STATUS_CHANGED;
            default -> throw new IllegalArgumentException(
                    "Unknown aggregateType for outbox routing: " + aggregateType);
        };
    }
}
