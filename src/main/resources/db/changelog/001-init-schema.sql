--liquibase formatted sql

-- ============================================================
-- Миграция 001: Начальная схема БД
-- Автор: crm-dev
-- ============================================================

--changeset crm-dev:001-users
-- BIGSERIAL = BIGINT + последовательность (sequence) + DEFAULT nextval(...)
-- Аналог IDENTITY в других СУБД.
CREATE TABLE users (
    id         BIGSERIAL    PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100),
    -- email хранится как строка; JPA маппит @Embedded Email.value → этот столбец
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    -- ENUM как VARCHAR: добавление нового значения в Java enum не сломает старые данные
    role       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
);
--rollback DROP TABLE users;

--changeset crm-dev:001-customers
CREATE TABLE customers (
    id         BIGSERIAL    PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(255),
    phone      VARCHAR(50),
    company    VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_customers_email UNIQUE (email)
);
--rollback DROP TABLE customers;

--changeset crm-dev:001-deals
CREATE TABLE deals (
    id             BIGSERIAL      PRIMARY KEY,
    title          VARCHAR(500)   NOT NULL,
    -- Money @Embeddable: два поля в одной таблице
    amount         DECIMAL(19, 2),
    currency       CHAR(3),
    status         VARCHAR(50)    NOT NULL,
    customer_id    BIGINT         REFERENCES customers (id) ON DELETE SET NULL,
    assigned_to_id BIGINT         REFERENCES users (id)     ON DELETE SET NULL,
    closed_at      TIMESTAMP,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    -- @Version для оптимистичной блокировки: Hibernate инкрементит при каждом UPDATE
    version        BIGINT         NOT NULL DEFAULT 0
);
--rollback DROP TABLE deals;

--changeset crm-dev:001-tasks
CREATE TABLE tasks (
    id             BIGSERIAL    PRIMARY KEY,
    title          VARCHAR(500) NOT NULL,
    description    TEXT,
    priority       VARCHAR(50)  NOT NULL,
    deal_id        BIGINT       REFERENCES deals (id) ON DELETE CASCADE,
    assigned_to_id BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    due_date       DATE,
    -- BOOLEAN DEFAULT FALSE: задача по умолчанию не выполнена
    completed      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
--rollback DROP TABLE tasks;

--changeset crm-dev:001-comments
CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    content    TEXT      NOT NULL,
    deal_id    BIGINT    NOT NULL REFERENCES deals (id) ON DELETE CASCADE,
    author_id  BIGINT    REFERENCES users (id)          ON DELETE SET NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
-- ON DELETE CASCADE: удаление сделки → автоматическое удаление всех её комментариев
-- на уровне БД (дополнительно к cascade JPA)
--rollback DROP TABLE comments;
