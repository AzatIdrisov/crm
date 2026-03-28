package com.crm.service;

import com.crm.event.DealStatusChangedEvent;
import com.crm.exception.ResourceNotFoundException;
import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import com.crm.repository.DealRepository;
import com.crm.repository.spec.DealSpecification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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

    public DealService(DealRepository dealRepository, ApplicationEventPublisher publisher) {
        this.dealRepository = dealRepository;
        this.publisher = publisher;
    }

    public Optional<Deal> findById(Long id) {
        return dealRepository.findById(id);
    }

    /** Загрузить сделку вместе с customer и assignedTo за один SELECT (JOIN FETCH). */
    public Optional<Deal> findByIdWithDetails(Long id) {
        return dealRepository.findByIdWithDetails(id);
    }

    public List<Deal> findAll() {
        return dealRepository.findAll();
    }

    @Transactional
    public Deal save(Deal deal) {
        return dealRepository.save(deal);
    }

    @Transactional
    public boolean deleteById(Long id) {
        if (!dealRepository.existsById(id)) {
            return false;
        }
        dealRepository.deleteById(id);
        return true;
    }

    /**
     * Изменить статус сделки и опубликовать событие.
     *
     * Нюанс: всё происходит в одной транзакции. Событие публикуется ДО commit —
     * @Async @EventListener запустится в другом потоке и может увидеть изменения
     * только после commit основной транзакции. Если нужна гарантия — использовать
     * TransactionalEventListener(phase = AFTER_COMMIT).
     */
    @Transactional
    public Deal changeStatus(Long id, DealStatus newStatus) {
        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));
        DealStatus oldStatus = deal.getStatus();
        deal.setStatus(newStatus);
        // save() не нужен — @Transactional + dirty checking обнаружит изменение
        // и сгенерирует UPDATE автоматически при commit.
        publisher.publishEvent(new DealStatusChangedEvent(this, id, oldStatus, newStatus));
        return deal;
    }

    /**
     * Массовое обновление статуса через @Modifying — без загрузки объекта.
     * Возвращает количество обновлённых строк.
     */
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
