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

    public CompletableFuture<Deal> loadDealAsync(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(300);
                log.info("Загрузка сделки {} в потоке {}", id, Thread.currentThread().getName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Deal.builder().id(id).build();
        }, executor);
    }

    // -------------------------------------------------------------------------
    // thenApply — преобразование результата
    // -------------------------------------------------------------------------

    public CompletableFuture<String> loadDealAsString(Long id) {
       return loadDealAsync(id)
                .thenApply(deal -> "Deal %s: title [%s]".formatted(deal.getId(), deal.getStatus()));
    }

    // -------------------------------------------------------------------------
    // thenCombine — объединение двух futures
    // -------------------------------------------------------------------------

    public CompletableFuture<List<Deal>> loadTwoDeals(Long id1, Long id2) {
        CompletableFuture<Deal> first = loadDealAsync(id1);
        CompletableFuture<Deal> second = loadDealAsync(id2);
        return first.thenCombine(second, List::of);
    }

    // -------------------------------------------------------------------------
    // exceptionally — обработка ошибок
    // -------------------------------------------------------------------------

    public CompletableFuture<Deal> loadWithFallback(Long id) {
        CompletableFuture<Deal> future = CompletableFuture.supplyAsync(() -> {
            if (id < 0) throw new IllegalArgumentException("Некорректный id: " + id);
            return Deal.builder().id(id).build();
        }, executor);
        return future.exceptionally(ex -> {
            log.error("Ошибка загрузки сделки {}: {}", id, ex.getMessage());
            return new Deal();
        });
    }

    // -------------------------------------------------------------------------
    // allOf — ждём все futures
    // -------------------------------------------------------------------------

    public CompletableFuture<List<Deal>> loadAll(List<Long> ids) {
        List<CompletableFuture<Deal>> futures = ids.stream().map(this::loadDealAsync).toList();
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return all.thenApply(v -> futures.stream().map(CompletableFuture::join).toList());

    }

}
