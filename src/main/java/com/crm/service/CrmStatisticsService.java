package com.crm.service;

import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Сервис статистики CRM — демонстрирует потокобезопасные примитивы.
 * <p>
 * Ключевые концепции:
 * - AtomicInteger          — атомарный счётчик без synchronized
 * - ReentrantLock          — явная блокировка с lock()/unlock() в finally
 * - ConcurrentHashMap      — потокобезопасная Map, computeIfAbsent, merge
 */
@Service
public class CrmStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(CrmStatisticsService.class);

    // -------------------------------------------------------------------------
    // AtomicInteger — счётчики операций
    // -------------------------------------------------------------------------

    private final AtomicInteger totalDealsCreated = new AtomicInteger(0);
    private final AtomicInteger totalDealsWon = new AtomicInteger(0);
    private final AtomicInteger totalDealsLost = new AtomicInteger(0);

    public int recordDealCreated() {
        return totalDealsCreated.incrementAndGet();
    }

    public void recordDealStatusChanged(DealStatus newStatus) {
        if (newStatus == DealStatus.WON) {
            totalDealsWon.incrementAndGet();
        } else if (newStatus == DealStatus.LOST) {
            totalDealsLost.incrementAndGet();
        }
    }

    public double getWinRate() {
        int created = totalDealsCreated.get();
        if (created == 0) return 0.0;
        return (double) totalDealsWon.get() / created * 100.0;
    }

    public void resetCounters() {
        totalDealsCreated.set(0);
        totalDealsWon.set(0);
        totalDealsLost.set(0);
    }

    // -------------------------------------------------------------------------
    // ReentrantLock — защита составной операции
    // -------------------------------------------------------------------------

    private final ReentrantLock reportLock = new ReentrantLock();
    private String lastReport = "";

    public void generateReport() {

        reportLock.lock();
        try {
            lastReport = String.format("Deals: created=%d, won=%d, lost=%d", totalDealsCreated.get(), totalDealsWon.get(), totalDealsLost.get());
        } finally {
            reportLock.unlock();
        }
    }

    public String getLastReport() {
        if (reportLock.tryLock()) {
            try {
                return lastReport;
            } finally {
                reportLock.unlock();
            }
        } else {
            return "Report is being generated...";
        }
    }

    // -------------------------------------------------------------------------
    // ConcurrentHashMap — кэш сделок и счётчики по статусам
    // -------------------------------------------------------------------------

    private final ConcurrentHashMap<Long, Deal> dealCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DealStatus, Integer> countByStatus = new ConcurrentHashMap<>();

    public boolean cacheDeal(Deal deal) {
        return dealCache.putIfAbsent(deal.getId(), deal) == null;
    }

    public Deal getOrLoad(Long id) {
        return dealCache.computeIfAbsent(id, this::loadFromDb);
    }

    public void indexByStatus(List<Deal> deals) {
        deals.forEach(deal -> countByStatus.merge(deal.getStatus(), 1, Integer::sum));
    }

    public int getCountByStatus(DealStatus status) {
        return countByStatus.getOrDefault(status, 0);
    }

    // -------------------------------------------------------------------------
    // Вспомогательный метод (заглушка "загрузки из БД")
    // -------------------------------------------------------------------------

    private Deal loadFromDb(Long id) {
        log.info("Загрузка сделки {} из БД", id);
        return Deal.builder().id(id).build();
    }
}
