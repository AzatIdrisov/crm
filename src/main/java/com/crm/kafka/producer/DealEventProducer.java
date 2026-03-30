package com.crm.kafka.producer;

import com.crm.kafka.config.KafkaTopics;
import com.crm.kafka.message.DealStatusChangedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Producer Kafka-событий об изменении статуса сделок.
 *
 * Ключевые концепции:
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * PARTITION KEY и гарантия порядка
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  kafkaTemplate.send(topic, key, value):
 *   Kafka использует key для определения партиции: partition = hash(key) % numPartitions.
 *   Все сообщения с одинаковым key → одна и та же партиция → строгий порядок.
 *
 *   Почему dealId как ключ:
 *   - Все события одной сделки (NEW→IN_PROGRESS→WON) → одна партиция.
 *   - Consumer читает партицию последовательно → порядок событий гарантирован.
 *   - Без ключа (null) → round-robin по партициям → события могут прийти не по порядку.
 *
 *   Ограничение: горячие ключи (hot partition).
 *   Если одна сделка генерирует 90% событий → одна партиция перегружена.
 *   Для равномерного распределения без ordering-гарантий — используй null-ключ.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * FIRE-AND-FORGET vs SYNCHRONOUS SEND
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  fire-and-forget (send()):
 *   kafkaTemplate.send() возвращает CompletableFuture<SendResult>.
 *   Мы НЕ блокируемся на нём — просто вешаем callback для логирования ошибок.
 *   Producer продолжает работу сразу. Ответ от брокера приходит асинхронно.
 *   Pros: низкая latency, не блокирует HTTP-поток.
 *   Cons: если брокер недоступен и retry исчерпаны — ошибка только в логах.
 *
 *  synchronous (sendSync()):
 *   kafkaTemplate.send(...).get() — блокирует текущий поток до подтверждения.
 *   Pros: если send() не бросил исключение — сообщение точно принято брокером.
 *   Cons: блокирует поток на время network round-trip (~1-5ms в LAN, больше при retry).
 *   Когда использовать: критически важные события, где потеря недопустима.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * RecordMetadata из SendResult
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  SendResult содержит RecordMetadata:
 *   - topic()     — топик куда записано
 *   - partition() — номер партиции
 *   - offset()    — позиция записи в партиции (монотонно возрастающий номер)
 *
 *  offset уникально идентифицирует запись: (topic, partition, offset).
 *  Логируем при успехе — полезно для трейсинга и дебага.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TRANSACTIONAL SEND
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  executeInTransaction(callback):
 *   1. Вызывает producer.initTransactions() (если ещё не инициализирован).
 *   2. producer.beginTransaction()
 *   3. Выполняет callback — все send() внутри принадлежат этой транзакции.
 *   4. producer.commitTransaction() при успехе.
 *   5. producer.abortTransaction() при исключении.
 *
 *  Зачем два разных KafkaTemplate:
 *   - Transactional template: transactional.id задан → может открывать транзакции.
 *     Нельзя вызвать send() вне executeInTransaction() — бросит исключение.
 *   - Обычный template: transactional.id НЕ задан → send() без транзакции.
 *     DeadLetterPublishingRecoverer использует обычный — чтобы DLT-запись
 *     не откатилась вместе с транзакцией основного сообщения.
 *
 *  Ограничение EOS (exactly-once semantics):
 *   Транзакция Kafka гарантирует атомарность НЕСКОЛЬКИХ Kafka-операций.
 *   НО: она не знает о базе данных.
 *   "UPDATE deals + publish to Kafka" — НЕ атомарны даже с transactional.id.
 *   Решение → Outbox Pattern (фаза 9.6).
 */
@Component
public class DealEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DealEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTemplate<String, Object> transactionalKafkaTemplate;

    public DealEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Qualifier("transactionalKafkaTemplate") KafkaTemplate<String, Object> transactionalKafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.transactionalKafkaTemplate = transactionalKafkaTemplate;
    }

    // ─────────────────────────────────────────────────────────
    // Fire-and-forget: основной метод для DealService
    // ─────────────────────────────────────────────────────────

    /**
     * Публикует событие асинхронно (fire-and-forget с callback).
     *
     * Ключ = dealId.toString() → все события одной сделки в одну партицию.
     * Callback логирует результат, но не блокирует вызывающий поток.
     */
    public void send(DealStatusChangedMessage message) {
        String key = message.dealId().toString();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaTopics.DEAL_STATUS_CHANGED, key, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Ошибка отправки события для сделки {}: {}",
                        message.dealId(), ex.getMessage(), ex);
            } else {
                var metadata = result.getRecordMetadata();
                log.debug("Событие для сделки {} отправлено: topic={} partition={} offset={}",
                        message.dealId(),
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset());
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    // Synchronous: блокируемся до подтверждения брокера
    // ─────────────────────────────────────────────────────────

    /**
     * Публикует событие синхронно — ждёт подтверждения от брокера.
     *
     * Бросает RuntimeException если брокер недоступен или истёк таймаут.
     * Используй когда потеря сообщения недопустима и latency некритична.
     */
    public void sendSync(DealStatusChangedMessage message) {
        String key = message.dealId().toString();
        try {
            SendResult<String, Object> result =
                    kafkaTemplate.send(KafkaTopics.DEAL_STATUS_CHANGED, key, message).get();
            var metadata = result.getRecordMetadata();
            log.info("Синхронная отправка подтверждена: partition={} offset={}",
                    metadata.partition(), metadata.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Прервано ожидание подтверждения Kafka", e);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка синхронной отправки в Kafka", e);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Transactional: exactly-once внутри Kafka
    // ─────────────────────────────────────────────────────────

    /**
     * Публикует событие в рамках Kafka-транзакции.
     *
     * executeInTransaction():
     *  - beginTransaction() → send() → commitTransaction()
     *  - При исключении → abortTransaction() автоматически
     *
     * Consumer с isolation.level=read_committed НЕ увидит это сообщение
     * пока транзакция не закоммичена.
     *
     * ВАЖНО: это транзакция Kafka, не базы данных.
     * Атомарность "DB + Kafka" обеспечивает только Outbox Pattern (фаза 9.6).
     */
    public void sendTransactional(DealStatusChangedMessage message) {
        String key = message.dealId().toString();

        transactionalKafkaTemplate.executeInTransaction(operations -> {
            operations.send(KafkaTopics.DEAL_STATUS_CHANGED, key, message);
            log.debug("Транзакционная отправка: dealId={} messageId={}",
                    message.dealId(), message.messageId());
            // Можно отправить в несколько топиков атомарно:
            // operations.send("another-topic", key, anotherMessage);
            // Оба попадут к consumer'у одновременно или не попадут вообще.
            return null;
        });
    }
}
