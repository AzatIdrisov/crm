package com.crm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Сервис уведомлений — демонстрирует работу с ExecutorService.
 *
 * Ключевые концепции:
 *  - Executors.newFixedThreadPool(n)  — фиксированный пул из n потоков
 *  - submit(Runnable)                 — запустить задачу, результат не нужен
 *  - submit(Callable)                 — запустить задачу, получить Future<T>
 *  - Future.get()                     — дождаться результата (блокирует поток)
 *  - shutdown() / awaitTermination()  — graceful завершение пула
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // Пул из 4 потоков — создаётся один раз при старте сервиса
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    // -------------------------------------------------------------------------
    // submit(Runnable) — fire and forget
    // -------------------------------------------------------------------------

    public void sendNotification(String message) {
        executor.submit(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("Уведомление отправлено: {}", message);
        });
    }

    public void sendBatch(List<String> messages) {
        log.info("Отправка {} уведомлений", messages.size());
        messages.forEach(this::sendNotification);
    }

    // -------------------------------------------------------------------------
    // submit(Callable) — задача с результатом
    // -------------------------------------------------------------------------

    public Future<String> sendWithResult(String message) {
        return executor.submit(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            return "OK: " + message;
        });
    }

    public String waitForResult(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Ошибка при получении результата", e);
            return "ERROR";
        } catch (ExecutionException e) {
            log.error("Ошибка при получении результата", e);
            return "ERROR";
        }
    }

    public List<String> sendBatchWithResult(List<String> messages) {
        return messages.stream()
                .map(this::sendWithResult)
                .map(this::waitForResult)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Graceful shutdown
    // -------------------------------------------------------------------------

    public void shutdown() {
        log.info("Завершение пула");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.info("Пул не завершился, принудительно завершаем");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Пул не завершился, принудительно завершаем");
            executor.shutdownNow();
        }
    }
}
