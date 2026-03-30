--liquibase formatted sql

-- ============================================================
-- Миграция 003: Таблица Outbox для Outbox Pattern
--
-- Outbox Pattern решает проблему dual write:
--  INSERT в outbox_messages происходит в той же транзакции, что
--  и бизнес-изменение (UPDATE deals). PostgreSQL гарантирует
--  атомарность — либо оба изменения закоммичены, либо ни одного.
--
--  Отдельный @Scheduled poller читает PENDING-записи и публикует
--  в Kafka, после чего переводит статус в SENT или FAILED.
-- ============================================================

--changeset crm-dev:003-outbox-table
CREATE TABLE outbox_messages
(
    id             BIGSERIAL PRIMARY KEY,
    -- Тип агрегата: "Deal", "Customer" — маршрутизация в Kafka-топик
    aggregate_type VARCHAR(100) NOT NULL,
    -- ID агрегата — Kafka partition key для ordering по объекту
    aggregate_id   VARCHAR(100) NOT NULL,
    -- Тип события: "DealStatusChanged" — consumer фильтрует без десериализации
    event_type     VARCHAR(100) NOT NULL,
    -- JSON-сериализованное DTO (TEXT — произвольный размер)
    payload        TEXT         NOT NULL,
    -- UUID — ключ идемпотентности для consumer'а
    message_id     VARCHAR(36)  NOT NULL UNIQUE,
    -- PENDING → SENT после публикации в Kafka
    -- PENDING → FAILED если Kafka недоступна
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- Заполняется poller'ом (null пока PENDING)
    processed_at   TIMESTAMPTZ,
    -- Аудит из BaseEntity
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
--rollback DROP TABLE outbox_messages;

--changeset crm-dev:003-outbox-status-idx
-- Критичный индекс: poller делает SELECT WHERE status='PENDING' при каждом тике.
-- Без индекса — Seq Scan всей таблицы. С индексом — Index Scan O(log n).
-- Partial index: только PENDING-записи попадают в индекс → меньше размер, быстрее обновление.
CREATE INDEX idx_outbox_status ON outbox_messages (status) WHERE status = 'PENDING';
--rollback DROP INDEX idx_outbox_status;

--changeset crm-dev:003-outbox-created-idx
-- Вторичный индекс для FIFO-сортировки по времени создания.
-- poller использует ORDER BY created_at ASC → старые события обрабатываются первыми.
CREATE INDEX idx_outbox_created_at ON outbox_messages (created_at ASC);
--rollback DROP INDEX idx_outbox_created_at;
