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

    /**
     * TODO 3.2.1: Атомарно увеличить totalDealsCreated на 1.
     *             Вернуть новое значение счётчика.
     *             Использовать incrementAndGet().
     */
    public int recordDealCreated() {
        return totalDealsCreated.incrementAndGet();
    }

    /**
     * TODO 3.2.2: В зависимости от статуса сделки атомарно увеличить
     *             totalDealsWon или totalDealsLost.
     *             Для остальных статусов — ничего не делать.
     *             Использовать incrementAndGet().
     */
    public void recordDealStatusChanged(DealStatus newStatus) {
        if (newStatus == DealStatus.WON) {
            totalDealsWon.incrementAndGet();
        } else if (newStatus == DealStatus.LOST) {
            totalDealsLost.incrementAndGet();
        }
    }

    /**
     * TODO 3.2.3: Вернуть конверсию: процент выигранных сделок от созданных.
     *             Если totalDealsCreated == 0 — вернуть 0.0.
     *             Использовать get() у AtomicInteger.
     *             Формула: (won / created) * 100.0
     */
    public double getWinRate() {
        return (double) totalDealsWon.get() / totalDealsCreated.get() * 100.0;
    }

    /**
     * TODO 3.2.4: Атомарно сбросить все три счётчика в 0.
     *             Использовать set(0).
     */
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

    /**
     * TODO 3.2.5: Сформировать отчёт по сделкам и сохранить в lastReport.
     *             Формат: "Deals: created=%d, won=%d, lost=%d"
     *             Операция чтения счётчиков + запись в lastReport должна быть
     *             атомарной — защитить её через reportLock.lock() / unlock().
     *             unlock() — ВСЕГДА в блоке finally.
     */
    public void generateReport() {

        reportLock.lock();
        try {
            lastReport = String.format("Deals: created=%d, won=%d, lost=%d", totalDealsCreated.get(), totalDealsWon.get(), totalDealsLost.get());
        } finally {
            reportLock.unlock();
        }
    }

    /**
     * TODO 3.2.6: Вернуть lastReport.
     *             Защитить чтение через reportLock (другой поток может писать).
     *             Использовать tryLock() — если лок занят, вернуть "Report is being generated...".
     */
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

    /**
     * TODO 3.2.7: Положить сделку в dealCache по её id.
     *             Если сделка с таким id уже есть — не перезаписывать.
     *             Использовать putIfAbsent().
     *             Вернуть true если добавили, false если уже была.
     */
    public boolean cacheDeal(Deal deal) {
        dealCache.putIfAbsent(deal.getId(), deal);
        return false;
    }

    /**
     * TODO 3.2.8: Получить сделку из кэша по id.
     *             Если в кэше нет — загрузить через loadFromDb(id) и положить в кэш.
     *             Использовать computeIfAbsent().
     */
    public Deal getOrLoad(Long id) {
        dealCache.computeIfAbsent(id, this::loadFromDb);
        return null;
    }

    /**
     * TODO 3.2.9: Для каждой сделки из списка увеличить счётчик её статуса в countByStatus на 1.
     *             Использовать merge(key, 1, Integer::sum).
     */
    public void indexByStatus(List<Deal> deals) {
        deals.forEach(deal -> countByStatus.merge(deal.getId, 1, Integer::sum);
    }

    /**
     * TODO 3.2.10: Вернуть количество сделок с данным статусом из countByStatus.
     *              Если статус не встречался — вернуть 0.
     *              Использовать getOrDefault().
     */
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
