package com.crm.outbox;

import com.crm.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * JPA-сущность для хранения исходящих Kafka-сообщений.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * OUTBOX PATTERN — решение проблемы dual write
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Проблема:
 *   В одном бизнес-действии нужно изменить состояние в БД И опубликовать событие
 *   в Kafka. Эти два действия не атомарны — между ними может произойти сбой:
 *
 *   Сценарий 1 (потеря события):
 *    ✓ UPDATE deals SET status = 'WON'  → commit в PostgreSQL
 *    ✗ kafkaTemplate.send()             → Kafka недоступна → событие потеряно
 *
 *   Сценарий 2 (фантомное событие):
 *    ✓ kafkaTemplate.send()             → сообщение у брокера
 *    ✗ commit deals                     → БД откатилась → событие без изменения в БД
 *
 *  Решение — Outbox Pattern:
 *   Вместо прямой отправки в Kafka → записываем событие в таблицу outbox_messages
 *   В ТОЙ ЖЕ транзакции, что и бизнес-изменение.
 *   PostgreSQL гарантирует атомарность: оба изменения закоммичены или оба откачены.
 *
 *   Отдельный @Scheduled Poller:
 *    - Читает PENDING-записи из outbox_messages
 *    - Публикует в Kafka
 *    - Обновляет статус в SENT (или FAILED при ошибке)
 *
 *  Kafka-публикация полностью вынесена за пределы DB-транзакции.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ПОЛЯ
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  aggregateType  — тип доменного объекта ("Deal", "Customer").
 *                   Для маршрутизации в нужный Kafka-топик без if-else.
 *
 *  aggregateId    — идентификатор объекта (dealId.toString()).
 *                   Используется как Kafka partition key → ordering по объекту.
 *
 *  eventType      — тип события ("DealStatusChanged").
 *                   Consumer может фильтровать без десериализации payload.
 *
 *  payload        — JSON-сериализованное DTO сообщения (DealStatusChangedMessage).
 *                   TEXT позволяет хранить произвольно большой JSON.
 *
 *  messageId      — UUID для идемпотентной обработки на стороне consumer.
 *                   Тот же UUID что в DealStatusChangedMessage.messageId.
 *
 *  status         — текущий статус: PENDING → SENT | FAILED.
 *
 *  processedAt    — когда poller опубликовал сообщение (null пока PENDING).
 *                   Хранится как Instant (UTC), не зависит от часового пояса.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ПОЧЕМУ НЕ НАСЛЕДУЕМ createdAt из BaseEntity
 * ─────────────────────────────────────────────────────────────────────────────
 *  BaseEntity использует @CreatedDate (тип LocalDateTime — локальное время JVM).
 *  Для Outbox нам нужен Instant (UTC) в processedAt чтобы сравнивать временные
 *  метки из разных сервисов/часовых поясов.
 *  createdAt берём из BaseEntity (LocalDateTime) — достаточно для мониторинга лага.
 */
@Entity
@Table(name = "outbox_messages")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class OutboxMessage extends BaseEntity<Long> {

    // Тип агрегата — для маршрутизации в топик без hardcode в poller'е
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    // ID агрегата — становится Kafka partition key
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    // Тип события — consumer может проверить без десериализации payload
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // JSON-сериализованное DTO — TEXT для произвольного размера
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    // UUID — ключ идемпотентности для consumer'а (совпадает с messageId в DTO)
    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    // PENDING → SENT после успешной публикации в Kafka
    // PENDING → FAILED если Kafka недоступна
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    // Заполняется poller'ом после отправки в Kafka (null пока PENDING)
    @Column(name = "processed_at")
    private Instant processedAt;
}
