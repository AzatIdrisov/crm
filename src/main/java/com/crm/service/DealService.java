package com.crm.service;

import com.crm.event.DealStatusChangedEvent;
import com.crm.exception.ResourceNotFoundException;
import com.crm.kafka.message.DealStatusChangedMessage;
import com.crm.kafka.producer.DealEventProducer;
import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import com.crm.config.CacheNames;
import com.crm.repository.DealRepository;
import com.crm.repository.spec.DealSpecification;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

/**
 * Сервис сделок — Phase 5: репозиторий вместо in-memory Map.
 *
 * Ключевые концепции:
 *  - @Transactional на changeStatus: метод открывает транзакцию, загружает Deal,
 *    меняет статус, сохраняет. Всё в ОДНОЙ транзакции — нет риска, что метод
 *    упадёт между load и save, оставив данные в непоследовательном состоянии.
 *
 *  - @Modifying updateStatus: альтернативный подход — массовое обновление без
 *    загрузки сущности. Используем когда нужно обновить много строк сразу.
 *
 *  - Specification.where(...).and(...): динамическая фильтрация без JPQL.
 *    Каждый критерий — отдельная Specification, которые комбинируются.
 */
@Service
@Transactional(readOnly = true)
public class DealService {

    private final DealRepository dealRepository;
    private final ApplicationEventPublisher publisher;
    private final DealEventProducer dealEventProducer;

    public DealService(DealRepository dealRepository,
                       ApplicationEventPublisher publisher,
                       DealEventProducer dealEventProducer) {
        this.dealRepository = dealRepository;
        this.publisher = publisher;
        this.dealEventProducer = dealEventProducer;
    }

    // findById — НЕ кэшируем: возвращает Deal без JOIN FETCH, lazy-поля могут быть прокси.
    // При сериализации в Redis Hibernate6Module запишет их как {"id":X} без данных.
    // Используй findByIdWithDetails если нужны данные customer/assignedTo.
    public Optional<Deal> findById(Long id) {
        return dealRepository.findById(id);
    }

    // findByIdWithDetails кэшируем: JOIN FETCH гарантирует что customer и assignedTo
    // полностью загружены до закрытия сессии → сериализация в Redis безопасна.
    @Cacheable(value = CacheNames.DEALS, key = "#id", unless = "#result.isEmpty()")
    public Optional<Deal> findByIdWithDetails(Long id) {
        return dealRepository.findByIdWithDetails(id);
    }

    public List<Deal> findAll() {
        return dealRepository.findAll();
    }

    @CacheEvict(value = CacheNames.DEALS, key = "#result.id")
    @Transactional
    public Deal save(Deal deal) {
        return dealRepository.save(deal);
    }

    @CacheEvict(value = CacheNames.DEALS, key = "#id")
    @Transactional
    public boolean deleteById(Long id) {
        if (!dealRepository.existsById(id)) {
            return false;
        }
        dealRepository.deleteById(id);
        return true;
    }

    /**
     * Изменить статус сделки, опубликовать Spring-событие и отправить в Kafka.
     *
     * ── Spring ApplicationEvent (in-process) ──────────────────────────────────
     * publisher.publishEvent() — синхронный вызов внутри JVM.
     * @Async @EventListener (DealStatusChangedListener) запустит обработчик
     * в отдельном потоке после возврата из publishEvent().
     *
     * ── Kafka (cross-process) ─────────────────────────────────────────────────
     * dealEventProducer.send() — fire-and-forget, не блокирует транзакцию.
     *
     * ── ПРОБЛЕМА DUAL WRITE ───────────────────────────────────────────────────
     * Текущий код содержит фундаментальную проблему: два независимых side-effect
     * внутри одной транзакции БД:
     *
     *   1. deal.setStatus(newStatus) → при commit → UPDATE в PostgreSQL
     *   2. dealEventProducer.send()  → немедленная отправка в Kafka
     *
     * Сценарий потери данных:
     *   а) send() успешно отправил в Kafka (сообщение у брокера)
     *   б) commit() упал (БД недоступна, constraint violation и т.д.)
     *   в) Kafka уже содержит событие, БД — нет. Состояния рассинхронизированы.
     *
     * Обратный сценарий:
     *   а) commit() успешен (статус изменён в БД)
     *   б) send() упал (Kafka недоступна)
     *   в) БД изменена, событие в Kafka потеряно навсегда.
     *
     * ── РЕШЕНИЕ: Outbox Pattern (фаза 9.6) ───────────────────────────────────
     * В одной транзакции: UPDATE deals + INSERT outbox_messages.
     * Отдельный @Scheduled poller читает outbox и публикует в Kafka.
     * Kafka-публикация полностью вынесена за пределы DB-транзакции.
     * Гарантия: либо оба изменения закоммичены, либо ни одного.
     */
    @CacheEvict(value = CacheNames.DEALS, key = "#id")
    @Transactional
    public Deal changeStatus(Long id, DealStatus newStatus) {
        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));
        DealStatus oldStatus = deal.getStatus();
        deal.setStatus(newStatus);
        // save() не нужен — @Transactional + dirty checking обнаружит изменение
        // и сгенерирует UPDATE автоматически при commit.

        // Spring ApplicationEvent: in-process уведомление (NotificationService, CrmStatisticsService)
        publisher.publishEvent(new DealStatusChangedEvent(this, id, oldStatus, newStatus));

        // Kafka: cross-process уведомление (fire-and-forget, DUAL WRITE — см. комментарий выше)
        dealEventProducer.send(new DealStatusChangedMessage(
                id,
                oldStatus,
                newStatus,
                Instant.now(),
                UUID.randomUUID().toString()
        ));

        return deal;
    }

    /**
     * Массовое обновление статуса через @Modifying — без загрузки объекта.
     * Возвращает количество обновлённых строк.
     */
    @CacheEvict(value = CacheNames.DEALS, key = "#id")
    @Transactional
    public int bulkUpdateStatus(Long id, DealStatus newStatus) {
        return dealRepository.updateStatus(id, newStatus);
    }

    /**
     * Динамическая фильтрация через Specification.
     * Любой из параметров может быть null — соответствующий фильтр игнорируется.
     */
    public List<Deal> search(DealStatus status, Long customerId, Long assignedToId, BigDecimal minAmount) {
        Specification<Deal> spec = Specification
                .where(DealSpecification.hasStatus(status))
                .and(DealSpecification.hasCustomer(customerId))
                .and(DealSpecification.assignedTo(assignedToId))
                .and(DealSpecification.amountGreaterThan(minAmount));
        return dealRepository.findAll(spec);
    }
}
