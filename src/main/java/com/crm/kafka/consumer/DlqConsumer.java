package com.crm.kafka.consumer;

import com.crm.kafka.config.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumer Dead Letter Topic — обрабатывает "ядовитые" сообщения.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ЧТО ТАКОЕ DLT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Dead Letter Topic (DLT) — топик для сообщений, которые не удалось обработать
 *  после всех попыток retry.
 *
 *  Кто записывает в DLT:
 *   DeadLetterPublishingRecoverer (из KafkaConfig) вызывается DefaultErrorHandler
 *   когда ExponentialBackOff исчерпал все попытки.
 *   Конвенция имён Spring Kafka: <original-topic>.DLT
 *
 *  Poison pill (ядовитое сообщение):
 *   Сообщение, которое невозможно обработать ни при каких условиях:
 *   - невалидный JSON (десериализация всегда падает)
 *   - бизнес-правило нарушено (dealId не существует)
 *   - баг в коде consumer'а (NPE, ClassCastException)
 *   Без DLT poison pill заблокировал бы партицию навсегда.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * DLT HEADERS — метаданные оригинальной ошибки
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  DeadLetterPublishingRecoverer добавляет заголовки в DLT-сообщение:
 *
 *  KafkaHeaders.DLT_ORIGINAL_TOPIC      — топик из которого пришло сообщение
 *  KafkaHeaders.DLT_ORIGINAL_PARTITION  — партиция (byte[] → int)
 *  KafkaHeaders.DLT_ORIGINAL_OFFSET     — offset (byte[] → long)
 *  KafkaHeaders.DLT_ORIGINAL_TIMESTAMP  — timestamp оригинального сообщения
 *  KafkaHeaders.DLT_EXCEPTION_FQCN      — полное имя класса исключения
 *  KafkaHeaders.DLT_EXCEPTION_MESSAGE   — сообщение исключения
 *  KafkaHeaders.DLT_EXCEPTION_STACKTRACE — стектрейс (может быть длинным)
 *
 *  Эти заголовки позволяют:
 *   1. Понять ЧТО пошло не так (тип и сообщение исключения).
 *   2. Найти оригинальное сообщение по (topic, partition, offset).
 *   3. Сделать replay: заново опубликовать оригинальное сообщение в topic.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * СТРАТЕГИИ ОБРАБОТКИ DLT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  1. Только логирование + алерт (наш подход):
 *     Инженер видит алерт → анализирует → решает вручную.
 *     Подходит для: нечастых ошибок, требующих ручного разбора.
 *
 *  2. Автоматический replay:
 *     DLQ consumer перечитывает сообщение, трансформирует и отправляет обратно.
 *     Риск: бесконечный цикл если баг не исправлен.
 *
 *  3. Запись в БД для аудита:
 *     Сохраняем failed_messages таблицу с полным контекстом ошибки.
 *     UI/API для ручного replay после исправления кода.
 *
 *  4. Мониторинг DLT-lag:
 *     Алерт если consumer_group lag на DLT-топике > 0 дольше N минут.
 *     Инструменты: Kafka Exporter + Prometheus + Grafana.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * DLT vs DLQ
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  DLQ (Dead Letter Queue) — термин из мира MQ (RabbitMQ, ActiveMQ, SQS).
 *   Очередь — строго FIFO, одно сообщение читает один consumer.
 *
 *  DLT (Dead Letter Topic) — Kafka-специфичный термин.
 *   Топик — может иметь несколько партиций, несколько consumer'ов.
 *   Сообщения не удаляются после чтения (retention policy).
 *   Можно перечитать с любого offset'а → возможность replay.
 *
 *  В контексте Spring Kafka термины часто используют взаимозаменяемо,
 *  но технически правильно — DLT.
 */
@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    /**
     * Читает сообщения из Dead Letter Topic.
     *
     * groupId = "crm-dlq-group": отдельная consumer group — не пересекается
     * с основной "crm-notification-group". Если DLT не читается долго,
     * можно мониторить lag этой группы отдельно.
     *
     * Принимаем ConsumerRecord<String, byte[]>:
     * Сообщение в DLT может быть невалидным JSON (именно поэтому оно и попало в DLT).
     * byte[] позволяет прочитать его даже если десериализация в DTO невозможна.
     */
    @KafkaListener(
            topics = KafkaTopics.DEAL_STATUS_CHANGED_DLT,
            groupId = "crm-dlq-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {

        // Читаем заголовки — они содержат метаданные оригинальной ошибки
        String originalTopic     = headerAsString(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        String originalPartition = headerAsString(record, KafkaHeaders.DLT_ORIGINAL_PARTITION);
        String originalOffset    = headerAsString(record, KafkaHeaders.DLT_ORIGINAL_OFFSET);
        String exceptionClass    = headerAsString(record, KafkaHeaders.DLT_EXCEPTION_FQCN);
        String exceptionMessage  = headerAsString(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);

        log.error("""
                        Сообщение попало в DLT после исчерпания retry.
                        Оригинал: topic={} partition={} offset={}
                        Ключ: {}
                        Исключение: {} — {}
                        Размер payload: {} bytes
                        """,
                originalTopic, originalPartition, originalOffset,
                record.key(),
                exceptionClass, exceptionMessage,
                record.value() != null ? record.value().length : 0
        );

        // TODO (prod): отправить алерт (PagerDuty, Slack, email)
        // TODO (prod): сохранить в failed_messages таблицу для ручного replay
    }

    /**
     * Читает заголовок Kafka как строку.
     *
     * Заголовки хранятся как byte[].
     * Числовые значения (partition=int, offset=long) DeadLetterPublishingRecoverer
     * записывает как строку, а не как бинарное представление числа.
     */
    private String headerAsString(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) {
            return "n/a";
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
