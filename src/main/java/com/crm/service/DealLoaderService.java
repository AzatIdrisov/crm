package com.crm.service;

import com.crm.model.Deal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сервис загрузки сделок — демонстрирует работу с CompletableFuture.
 *
 * Ключевые концепции:
 *  - CompletableFuture.supplyAsync()   — запустить асинхронно, вернуть результат
 *  - CompletableFuture.runAsync()      — запустить асинхронно, без результата
 *  - thenApply(fn)                     — преобразовать результат (Function)
 *  - thenAccept(fn)                    — использовать результат (Consumer)
 *  - thenCombine(other, fn)            — объединить два CompletableFuture
 *  - exceptionally(fn)                 — обработать исключение, вернуть дефолт
 *  - allOf(futures...)                 — дождаться всех
 */
@Service
public class DealLoaderService {

    private static final Logger log = LoggerFactory.getLogger(DealLoaderService.class);

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final DealAnalyticsService analyticsService;

    public DealLoaderService(DealAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    // -------------------------------------------------------------------------
    // supplyAsync — асинхронная загрузка
    // -------------------------------------------------------------------------

    /**
     * TODO 3.1.6: Загрузить сделку асинхронно через CompletableFuture.supplyAsync().
     *             Внутри: сделать Thread.sleep(300) (имитация БД),
     *             залогировать "Загрузка сделки {id} в потоке {threadName}",
     *             вернуть заглушку: new Deal() с id.
     *             Передать executor вторым аргументом в supplyAsync.
     */
    public CompletableFuture<Deal> loadDealAsync(Long id) {
        return null;
    }

    // -------------------------------------------------------------------------
    // thenApply — преобразование результата
    // -------------------------------------------------------------------------

    /**
     * TODO 3.1.7: Загрузить сделку через loadDealAsync(id),
     *             затем через thenApply() преобразовать в строку:
     *             "Deal #id: title [status]"
     */
    public CompletableFuture<String> loadDealAsString(Long id) {
        return null;
    }

    // -------------------------------------------------------------------------
    // thenCombine — объединение двух futures
    // -------------------------------------------------------------------------

    /**
     * TODO 3.1.8: Загрузить две сделки параллельно через loadDealAsync(),
     *             объединить через thenCombine() в список List<Deal>.
     *             Обе загрузки должны идти параллельно, не последовательно.
     */
    public CompletableFuture<List<Deal>> loadTwoDeals(Long id1, Long id2) {
        return null;
    }

    // -------------------------------------------------------------------------
    // exceptionally — обработка ошибок
    // -------------------------------------------------------------------------

    /**
     * TODO 3.1.9: Загрузить сделку, но если id < 0 — бросить IllegalArgumentException.
     *             Через exceptionally() поймать ошибку, залогировать её,
     *             вернуть new Deal() как заглушку (fallback).
     */
    public CompletableFuture<Deal> loadWithFallback(Long id) {
        return null;
    }

    // -------------------------------------------------------------------------
    // allOf — ждём все futures
    // -------------------------------------------------------------------------

    /**
     * TODO 3.1.10: Загрузить список сделок параллельно.
     *              Для каждого id вызвать loadDealAsync(),
     *              собрать все CompletableFuture через allOf(),
     *              после завершения всех — собрать результаты в List<Deal>.
     *
     *  Подсказка: CompletableFuture<Void> all = CompletableFuture.allOf(futures[])
     *             all.thenApply(_ -> stream из futures -> .join())
     */
    public CompletableFuture<List<Deal>> loadAll(List<Long> ids) {
        return null;
    }
}
