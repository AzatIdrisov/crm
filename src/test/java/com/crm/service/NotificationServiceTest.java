package com.crm.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {

    private final NotificationService service = new NotificationService();

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    // -------------------------------------------------------------------------
    // 3.1.1 sendNotification — fire and forget
    // -------------------------------------------------------------------------

    @Test
    void sendNotification_doesNotBlock() {
        // Метод должен вернуть управление сразу, не ожидая sleep(100)
        long start = System.currentTimeMillis();
        service.sendNotification("test");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(50);
    }

    @Test
    void sendNotification_doesNotThrow() {
        // Не бросает исключений при нормальном вызове
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.sendNotification("hello")
        );
    }

    // -------------------------------------------------------------------------
    // 3.1.2 sendBatch
    // -------------------------------------------------------------------------

    @Test
    void sendBatch_doesNotBlock() {
        // Пакет из 3 сообщений — метод не должен ждать выполнения задач
        long start = System.currentTimeMillis();
        service.sendBatch(List.of("msg1", "msg2", "msg3"));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(100);
    }

    @Test
    void sendBatch_emptyList_doesNotThrow() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.sendBatch(List.of())
        );
    }

    // -------------------------------------------------------------------------
    // 3.1.3 sendWithResult — submit(Callable), возвращает Future
    // -------------------------------------------------------------------------

    @Test
    void sendWithResult_returnsFutureImmediately() {
        long start = System.currentTimeMillis();
        Future<String> future = service.sendWithResult("ping");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(future).isNotNull();
        assertThat(elapsed).isLessThan(50); // не блокируется
    }

    @Test
    void sendWithResult_futureContainsOkPrefix() throws Exception {
        Future<String> future = service.sendWithResult("ping");
        String result = future.get(); // ждём результата

        assertThat(result).isEqualTo("OK: ping");
    }

    // -------------------------------------------------------------------------
    // 3.1.4 waitForResult — Future.get() с обработкой ошибок
    // -------------------------------------------------------------------------

    @Test
    void waitForResult_returnsResult() {
        Future<String> future = service.sendWithResult("hello");
        String result = service.waitForResult(future);

        assertThat(result).isEqualTo("OK: hello");
    }

    @Test
    void waitForResult_cancelledFuture_returnsError() {
        Future<String> future = service.sendWithResult("hello");
        future.cancel(true); // отменяем задачу

        String result = service.waitForResult(future);

        assertThat(result).isEqualTo("ERROR");
    }

    // -------------------------------------------------------------------------
    // 3.1.5 sendBatchWithResult
    // -------------------------------------------------------------------------

    @Test
    void sendBatchWithResult_returnsAllResults() {
        List<String> results = service.sendBatchWithResult(List.of("a", "b", "c"));

        assertThat(results).containsExactly("OK: a", "OK: b", "OK: c");
    }

    @Test
    void sendBatchWithResult_emptyList_returnsEmptyList() {
        List<String> results = service.sendBatchWithResult(List.of());

        assertThat(results).isEmpty();
    }

    // -------------------------------------------------------------------------
    // shutdown
    // -------------------------------------------------------------------------

    @Test
    void shutdown_doesNotThrow() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.shutdown()
        );
    }
}