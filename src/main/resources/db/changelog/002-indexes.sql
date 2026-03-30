--liquibase formatted sql

-- ============================================================
-- Миграция 002: Индексы для производительности
--
-- Принципы выбора индексов:
--  - Индексируем FK-колонки (customer_id, assigned_to_id, deal_id):
--    без индекса JOIN по FK — полное сканирование таблицы (Seq Scan).
--  - Индексируем колонки фильтрации WHERE (status, completed, due_date).
--  - НЕ индексируем всё подряд: каждый индекс замедляет INSERT/UPDATE/DELETE.
--  - UNIQUE-индексы уже созданы через CONSTRAINT в таблицах (email).
-- ============================================================

--changeset crm-dev:002-idx-deals-status
-- Запросы "все сделки со статусом NEW" — типичный фильтр CRM
CREATE INDEX idx_deals_status ON deals (status);
--rollback DROP INDEX idx_deals_status;

--changeset crm-dev:002-idx-deals-customer
-- JOIN deals на customer_id — без индекса Full Scan по deals при любом JOIN
CREATE INDEX idx_deals_customer_id ON deals (customer_id);
--rollback DROP INDEX idx_deals_customer_id;

--changeset crm-dev:002-idx-deals-assigned-to
-- Запросы "мои сделки" (assigned_to_id = ?) — очень частые в CRM
CREATE INDEX idx_deals_assigned_to_id ON deals (assigned_to_id);
--rollback DROP INDEX idx_deals_assigned_to_id;

--changeset crm-dev:002-idx-deals-status-assigned
-- Составной индекс для частого запроса: сделки по статусу + ответственному
-- Порядок важен: сначала более селективное поле (assigned_to_id).
-- Этот индекс покрывает и запросы только по assigned_to_id (leftmost prefix rule).
CREATE INDEX idx_deals_assigned_status ON deals (assigned_to_id, status);
--rollback DROP INDEX idx_deals_assigned_status;

--changeset crm-dev:002-idx-tasks-deal
-- Загрузка задач по сделке: SELECT * FROM tasks WHERE deal_id = ?
CREATE INDEX idx_tasks_deal_id ON tasks (deal_id);
--rollback DROP INDEX idx_tasks_deal_id;

--changeset crm-dev:002-idx-tasks-assigned-to
CREATE INDEX idx_tasks_assigned_to_id ON tasks (assigned_to_id);
--rollback DROP INDEX idx_tasks_assigned_to_id;

--changeset crm-dev:002-idx-tasks-due-date
-- Запросы просроченных задач: WHERE due_date < NOW() AND completed = false
CREATE INDEX idx_tasks_due_date ON tasks (due_date) WHERE completed = FALSE;
--rollback DROP INDEX idx_tasks_due_date;

--changeset crm-dev:002-idx-comments-deal
-- Загрузка всех комментариев к сделке
CREATE INDEX idx_comments_deal_id ON comments (deal_id);
--rollback DROP INDEX idx_comments_deal_id;
