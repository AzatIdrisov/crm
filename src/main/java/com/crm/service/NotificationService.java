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

    /**
     * TODO 3.1.1: Отправить одно уведомление асинхронно через executor.submit().
     *             Внутри Runnable: сделать Thread.sleep(100) (имитация отправки),
     *             затем залогировать "Уведомление отправлено: {message}"
     *             Исключения InterruptedException — пробросить через Thread.currentThread().interrupt()
     */
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

    /**
     * TODO 3.1.2: Отправить список уведомлений — для каждого вызвать sendNotification().
     *             Залогировать "Отправка {n} уведомлений" перед отправкой.
     */
    public void sendBatch(List<String> messages) {
        log.info("Отправка {} уведомлений", messages.size());
        messages.forEach(this::sendNotification);
    }

    // -------------------------------------------------------------------------
    // submit(Callable) — задача с результатом
    // -------------------------------------------------------------------------

    /**
     * TODO 3.1.3: Отправить уведомление и вернуть Future<String> с результатом.
     *             Callable должен: сделать Thread.sleep(200), вернуть "OK: {message}".
     *             Метод сразу возвращает Future — не ждёт выполнения.
     */
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

    /**
     * TODO 3.1.4: Дождаться результата из Future.get() и вернуть его.
     *             Обработать ExecutionException и InterruptedException —
     *             залогировать ошибку и вернуть "ERROR".
     */
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

    /**
     * TODO 3.1.5: Отправить список уведомлений и дождаться результатов.
     */
    public List<String> sendBatchWithResult(List<String> messages) {
        return messages.stream()
                .map(this::sendWithResult)
                .map(this::waitForResult)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Graceful shutdown
    // -------------------------------------------------------------------------

    /**
     * TODO 3.1.5: Корректно завершить пул:
     *             1. executor.shutdown()              — перестать принимать новые задачи
     *             2. awaitTermination(5, SECONDS)     — подождать завершения текущих
     *             3. Если не завершился — executor.shutdownNow()  — принудительно
     *             Залогировать каждый шаг.
     */
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
