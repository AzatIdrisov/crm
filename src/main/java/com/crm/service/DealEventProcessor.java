package com.crm.service;

import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Фоновая обработка событий сделок — демонстрирует BlockingQueue и ScheduledExecutorService.
 *
 * Ключевые концепции:
 *  - LinkedBlockingQueue        — потокобезопасная очередь; put() блокирует если полна,
 *                                 take() блокирует если пуста
 *  - Producer-Consumer          — один поток кладёт события, другой забирает и обрабатывает
 *  - ScheduledExecutorService   — запуск задач по расписанию
 *  - scheduleAtFixedRate()      — каждые N секунд от старта предыдущего запуска
 *  - scheduleWithFixedDelay()   — каждые N секунд после завершения предыдущего запуска
 *  - schedule()                 — одноразовый запуск с задержкой
 */
@Service
public class DealEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(DealEventProcessor.class);

    // Запись о событии сделки (статус изменился)
    public record DealEvent(Long dealId, DealStatus newStatus) {}

    // Ограниченная очередь событий — не более 100 элементов
    private final BlockingQueue<DealEvent> eventQueue = new LinkedBlockingQueue<>(100);

    // Флаг для остановки consumer-потока
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Планировщик периодических задач
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final CrmStatisticsService statisticsService;

    public DealEventProcessor(CrmStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    // =========================================================================
    // BlockingQueue — Producer
    // =========================================================================

    /**
     * TODO 3.3.1: Добавить событие в очередь.
     *             Использовать offer(event, timeout, TimeUnit) — неблокирующий вариант с таймаутом.
     *             Если очередь заполнена и за 500 мс место не освободилось — залогировать предупреждение.
     *             Метод бросает InterruptedException — пробросить его в сигнатуре.
     */
    public void publishEvent(Long dealId, DealStatus newStatus) throws InterruptedException {
    }

    /**
     * TODO 3.3.2: Добавить событие в очередь без ожидания.
     *             Использовать offer(event) — возвращает false если очередь полна.
     *             Если вернул false — залогировать предупреждение "Очередь переполнена, событие отброшено".
     */
    public void publishEventNonBlocking(Long dealId, DealStatus newStatus) {
    }

    // =========================================================================
    // BlockingQueue — Consumer
    // =========================================================================

    /**
     * TODO 3.3.3: Запустить consumer-поток через новый Thread (не executor).
     *             Установить running = true.
     *             В цикле пока running == true:
     *               - забрать событие через poll(1, TimeUnit.SECONDS) — с таймаутом, не take()
     *               - если событие не null — вызвать processEvent(event)
     *             После выхода из цикла — залогировать "Consumer остановлен".
     *             Поймать InterruptedException, восстановить флаг прерывания.
     *             Почему poll() а не take(): take() заблокирует навсегда и не проверит running.
     */
    public void startConsumer() {
    }

    /**
     * TODO 3.3.4: Остановить consumer-поток.
     *             Установить running = false.
     *             Залогировать "Остановка consumer".
     */
    public void stopConsumer() {
    }

    /**
     * TODO 3.3.5: Обработать одно событие из очереди.
     *             Залогировать "Обработка события: dealId={}, status={}".
     *             Вызвать statisticsService.recordDealStatusChanged(event.newStatus()).
     */
    private void processEvent(DealEvent event) {
    }

    // =========================================================================
    // ScheduledExecutorService
    // =========================================================================

    /**
     * TODO 3.3.6: Запускать generateReport() каждые 10 секунд с начальной задержкой 5 секунд.
     *             Использовать scheduler.scheduleAtFixedRate().
     *             Вернуть ScheduledFuture (не игнорировать возвращаемое значение — нужно для 3.3.9).
     *
     *             Отличие от scheduleWithFixedDelay:
     *              - scheduleAtFixedRate: каждые 10с от СТАРТА задачи (может накладываться если задача долгая)
     *              - scheduleWithFixedDelay: каждые 10с от ЗАВЕРШЕНИЯ задачи (следующий запуск ждёт конца текущего)
     */
    public java.util.concurrent.ScheduledFuture<?> startPeriodicReport() {
        return null;
    }

    /**
     * TODO 3.3.7: Запускать cleanupQueue() каждые 30 секунд после завершения предыдущего запуска.
     *             Использовать scheduler.scheduleWithFixedDelay().
     *             Начальная задержка — 0 секунд.
     */
    public void startQueueCleanup() {
    }

    /**
     * TODO 3.3.8: Запланировать одноразовую задачу: через 60 секунд залогировать
     *             "Напоминание: проверь незакрытые сделки".
     *             Использовать scheduler.schedule().
     */
    public void scheduleReminder() {
    }

    /**
     * TODO 3.3.9: Graceful shutdown планировщика и consumer.
     *             1. Вызвать stopConsumer()
     *             2. Вызвать scheduler.shutdown()
     *             3. Подождать завершения через awaitTermination(5, SECONDS)
     *             4. Если не завершился — вызвать shutdownNow()
     *             Поймать InterruptedException, восстановить флаг, вызвать shutdownNow().
     */
    public void shutdown() {
    }

    // =========================================================================
    // Вспомогательные методы
    // =========================================================================

    private void generateReport() {
        statisticsService.generateReport();
        log.info("Отчёт сформирован: {}", statisticsService.getLastReport());
    }

    private void cleanupQueue() {
        int size = eventQueue.size();
        if (size > 80) {
            eventQueue.clear();
            log.warn("Очередь очищена принудительно, было {} событий", size);
        }
    }

    public int getQueueSize() {
        return eventQueue.size();
    }
}
