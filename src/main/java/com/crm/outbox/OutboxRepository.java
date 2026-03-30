package com.crm.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий Outbox-сообщений.
 *
 * findTop100ByStatusOrderByCreatedAtAsc:
 *
 *  findTop100  — ограничение батча poller'а.
 *   Без лимита poller может считать миллионы PENDING-записей при накоплении лага.
 *   100 записей за итерацию — разумный компромисс между throughput и нагрузкой на БД.
 *   При fixedDelay=1000ms: 100 msg/s → достаточно для большинства CRM-сценариев.
 *
 *  ByStatus    — фильтр по индексированной колонке status.
 *   Без индекса idx_outbox_status → Seq Scan всей таблицы при каждом вызове poller'а.
 *   С индексом → Index Scan → O(log n) вместо O(n).
 *
 *  OrderByCreatedAtAsc — FIFO-порядок обработки.
 *   Старые события публикуются раньше новых.
 *   Важно для ordering: если сделка изменила статус дважды быстро,
 *   первое изменение должно прийти в Kafka раньше второго.
 */
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
