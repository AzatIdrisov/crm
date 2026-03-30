package com.crm.kafka.message;

import com.crm.model.enums.DealStatus;

import java.time.Instant;

/**
 * DTO сообщения Kafka для события изменения статуса сделки.
 *
 * Ключевые концепции:
 *
 *  Java record vs обычный класс:
 *   - record автоматически генерирует конструктор, геттеры, equals/hashCode/toString.
 *   - Immutable по природе — все поля final, сеттеров нет.
 *   - Идеален для DTO/сообщений: неизменяемость исключает случайную мутацию при обработке.
 *
 *  Kafka DTO vs Spring ApplicationEvent:
 *   - ApplicationEvent (DealStatusChangedEvent) — для внутреннего in-process общения
 *     внутри одного JVM. Не сериализуется, может содержать ссылки на бины.
 *   - Kafka message — пересекает границы процессов/сервисов. Должен:
 *     а) Сериализоваться в JSON (все поля — простые типы или сериализуемые классы).
 *     б) Быть версионируемым (обратная совместимость при добавлении полей).
 *     в) Быть самодостаточным (не зависеть от контекста отправителя).
 *
 *  messageId (UUID):
 *   - Уникальный идентификатор этого конкретного сообщения.
 *   - Используется consumer'ом для идемпотентной обработки:
 *     "если messageId уже обработан — пропустить".
 *   - Критично при at-least-once семантике: одно событие может прийти дважды
 *     (после rebalance, retry, restart consumer'а).
 *
 *  occurredAt (event time) vs processedAt (processing time):
 *   - occurredAt: момент когда событие ПРОИЗОШЛО в домене (deal изменил статус).
 *     Записывается producer'ом. Не меняется при повторной доставке.
 *   - processedAt: момент когда consumer ОБРАБОТАЛ сообщение.
 *     Определяется на стороне consumer'а.
 *   - Разница важна для: аудита, дедупликации по времени, event sourcing.
 *   - Пример: сообщение произошло в 10:00 (occurredAt), доставлено в 10:05
 *     из-за retry, обработано в 10:05 (processedAt). Разница = propagation delay.
 *
 *  Сериализация Instant в JSON:
 *   - Jackson с JavaTimeModule сериализует Instant как ISO-8601 строку:
 *     "2024-03-15T10:30:00Z" (при WRITE_DATES_AS_TIMESTAMPS=false).
 *   - Наш JacksonConfig уже настроен правильно (JavaTimeModule + disable TIMESTAMPS).
 *
 *  Schema evolution (эволюция схемы):
 *   - При добавлении нового поля в record — старые consumer'ы игнорируют его (JSON).
 *   - При удалении поля — старые consumer'ы получат null (если поле было nullable).
 *   - Никогда не переименовывай поля без версионирования схемы.
 *   - Для строгого версионирования используют Schema Registry (Confluent) + Avro/Protobuf.
 */
public record DealStatusChangedMessage(

        // Идентификатор сделки — используется как ключ партицонирования.
        // Все события одной сделки → одна партиция → порядок гарантирован.
        Long dealId,

        DealStatus oldStatus,
        DealStatus newStatus,

        // Event time: когда событие произошло в домене.
        // Instant = UTC timestamp, не зависит от часового пояса.
        Instant occurredAt,

        // Ключ идемпотентности на стороне consumer'а.
        // UUID генерируется producer'ом при каждой отправке.
        // Формат: java.util.UUID.randomUUID().toString()
        String messageId
) {
}
