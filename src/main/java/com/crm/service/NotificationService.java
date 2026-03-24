package com.crm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    }

    /**
     * TODO 3.1.2: Отправить список уведомлений — для каждого вызвать sendNotification().
     *             Залогировать "Отправка {n} уведомлений" перед отправкой.
     */
    public void sendBatch(List<String> messages) {

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
        return null;
    }

    /**
     * TODO 3.1.4: Дождаться результата из Future.get() и вернуть его.
     *             Обработать ExecutionException и InterruptedException —
     *             залогировать ошибку и вернуть "ERROR".
     */
    public String waitForResult(Future<String> future) {
        return null;
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

    }
}
