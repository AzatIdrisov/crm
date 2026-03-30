package com.crm.kafka.consumer;

import com.crm.kafka.config.KafkaTopics;
import com.crm.kafka.message.DealStatusChangedMessage;
import com.crm.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Consumer событий изменения статуса сделок.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * AckMode.MANUAL_IMMEDIATE и at-least-once
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Offset — позиция записи в партиции (0, 1, 2, ...).
 *  Committed offset — позиция, до которой consumer подтвердил обработку.
 *  Kafka хранит committed offset'ы в служебном топике __consumer_offsets.
 *
 *  При AckMode.MANUAL_IMMEDIATE:
 *   - Offset коммитится ТОЛЬКО при явном вызове ack.acknowledge().
 *   - Если consumer упал до acknowledge() — при перезапуске он получит
 *     то же сообщение снова (offset не был закоммичен).
 *   - Это и есть at-least-once: сообщение придёт МИНИМУМ один раз,
 *     но может прийти повторно при сбое → consumer обязан быть идемпотентным.
 *
 *  Антипаттерн (at-most-once):
 *   ack.acknowledge(); // сначала коммитим
 *   processMessage();  // потом обрабатываем — при сбое тут сообщение потеряно
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ИДЕМПОТЕНТНОСТЬ через Redis
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Когда consumer может получить дубликат:
 *   1. Rebalance: брокер переназначил партицию другому consumer'у до коммита offset'а.
 *   2. Consumer restart: приложение упало после обработки, но до коммита.
 *   3. Producer retry: idempotent producer дедуплицирует на брокере по (PID, seq),
 *      но после рестарта producer'а PID меняется — дубликаты возможны.
 *
 *  Стратегия дедупликации:
 *   - Ключ: "kafka:processed:{messageId}" в Redis.
 *   - Перед обработкой: проверить hasKey() → если есть, пропустить.
 *   - После обработки: set(key, "1", TTL=24h) → отметить как обработанное.
 *   - TTL — компромисс: слишком короткий → дубликаты не поймаем,
 *     слишком длинный → Redis хранит много старых ключей.
 *   - 24h достаточно: на практике Kafka retry происходят в течение минут/часов.
 *
 *  Проблема TOCTOU (time-of-check-time-of-use):
 *   Два consumer-потока могут одновременно пройти проверку hasKey() → оба обработают.
 *   Решение: использовать setIfAbsent() (Redis SET NX) — атомарная операция.
 *   Текущая реализация использует setIfAbsent() для атомарной проверки+записи.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ConsumerRecord METADATA
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  record.topic()     — название топика
 *  record.partition() — номер партиции (0, 1, 2 для нашего топика с 3 партициями)
 *  record.offset()    — позиция в партиции (уникальна в рамках партиции)
 *  record.key()       — ключ (dealId.toString() у нас)
 *  record.timestamp() — timestamp записи (установлен producer'ом или брокером)
 *
 *  Уникальный идентификатор записи в Kafka: (topic, partition, offset).
 *  Используется для трейсинга, replay и дебага.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * RETRY и DefaultErrorHandler
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Если consume() бросает исключение:
 *   - НЕ вызываем ack.acknowledge() — offset не коммитится.
 *   - DefaultErrorHandler (из KafkaConfig) перехватывает исключение.
 *   - Делает retry согласно ExponentialBackOff: 1s → 2s → 4s → ...
 *   - После исчерпания backoff → DeadLetterPublishingRecoverer отправляет в DLT.
 *   - DefaultErrorHandler вызывает acknowledge() самостоятельно после DLT-публикации.
 *
 *  Важно: при retry Kafka не делает новый poll() — она "перематывает" offset назад
 *  и переотправляет то же сообщение. Это реализуется через SeekToCurrentErrorHandler
 *  (устаревший) или DefaultErrorHandler (актуальный).
 */
@Component
public class DealEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DealEventConsumer.class);

    private static final String PROCESSED_KEY_PREFIX = "kafka:processed:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    public DealEventConsumer(NotificationService notificationService,
                             StringRedisTemplate redisTemplate) {
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Обрабатывает событие изменения статуса сделки.
     *
     * Параметры метода — Spring Kafka резолвит их автоматически:
     *  - ConsumerRecord<String, DealStatusChangedMessage>: сырая запись с метаданными
     *  - Acknowledgment: объект для ручного коммита offset'а
     *
     * @KafkaListener параметры:
     *  - topics: список топиков (можно несколько: {"topic1", "topic2"})
     *  - groupId: переопределяет group.id из ConsumerFactory (опционально)
     *  - containerFactory: имя бина ConcurrentKafkaListenerContainerFactory
     */
    @KafkaListener(
            topics = KafkaTopics.DEAL_STATUS_CHANGED,
            groupId = "crm-notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DealStatusChangedMessage> record,
                        Acknowledgment ack) {

        // 9.5.3 — логируем metadata до обработки
        log.debug("Получено сообщение: topic={} partition={} offset={} key={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key());

        DealStatusChangedMessage message = record.value();

        // 9.5.2 — идемпотентность: пропускаем дубликаты
        if (isDuplicate(message.messageId())) {
            log.warn("Дубликат сообщения messageId={} dealId={} — пропускаем",
                    message.messageId(), message.dealId());
            // Коммитим offset: сообщение «обработано» (пропущено как дубликат)
            ack.acknowledge();
            return;
        }

        // Основная обработка
        String notification = "Сделка %d: статус изменён %s → %s"
                .formatted(message.dealId(), message.oldStatus(), message.newStatus());
        notificationService.sendNotification(notification);

        log.info("Обработано событие: dealId={} {} → {} messageId={}",
                message.dealId(),
                message.oldStatus(),
                message.newStatus(),
                message.messageId());

        // Отмечаем messageId как обработанный ПОСЛЕ успешной обработки
        markAsProcessed(message.messageId());

        // Коммитим offset: подтверждаем Kafka что сообщение обработано
        // При at-least-once: если упадём между markAsProcessed() и acknowledge(),
        // получим то же сообщение снова — но isDuplicate() его отбросит.
        ack.acknowledge();
    }

    // ─────────────────────────────────────────────────────────
    // Дедупликация через Redis
    // ─────────────────────────────────────────────────────────

    /**
     * Атомарно проверяет и регистрирует messageId в Redis (SET NX + EX).
     *
     * setIfAbsent() = Redis SET key value NX EX ttl:
     *  - NX: только если ключ НЕ существует
     *  - EX: TTL в секундах
     *  Возвращает true если ключ был создан (первый раз), false если уже существовал.
     *
     * Атомарность предотвращает TOCTOU-гонку при concurrency=3:
     * два потока не смогут оба пройти проверку одновременно.
     *
     * @return true если сообщение уже обработано (дубликат), false если новое
     */
    private boolean isDuplicate(String messageId) {
        String key = PROCESSED_KEY_PREFIX + messageId;
        // setIfAbsent = SET NX: вернёт false если ключ уже есть (дубликат)
        Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(key, "1", DEDUP_TTL);
        return Boolean.FALSE.equals(wasAbsent);
    }

    /**
     * Обновляет TTL существующего ключа.
     *
     * Вызывается после обработки — сбрасывает TTL на полные 24 часа.
     * Это гарантирует что messageId не «протухнет» пока возможны поздние дубликаты.
     */
    private void markAsProcessed(String messageId) {
        redisTemplate.expire(PROCESSED_KEY_PREFIX + messageId, DEDUP_TTL);
    }
}
