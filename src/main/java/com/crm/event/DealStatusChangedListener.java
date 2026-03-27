package com.crm.event;

import com.crm.service.CrmStatisticsService;
import com.crm.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Слушатель событий изменения статуса сделки.
 *
 * Ключевые концепции:
 *  - @EventListener         — Spring вызовет метод при публикации нужного события
 *  - @Async                 — метод выполняется в отдельном потоке (не блокирует publisher)
 *  - @EnableAsync           — должна стоять на @Configuration классе, иначе @Async игнорируется
 *
 * Отличие от прямого вызова сервиса:
 *  - Publisher не знает о слушателях → слабая связанность
 *  - Можно добавить нового слушателя без изменения DealService
 */
@Component
public class DealStatusChangedListener {

    private static final Logger log = LoggerFactory.getLogger(DealStatusChangedListener.class);

    private final NotificationService notificationService;
    private final CrmStatisticsService statisticsService;

    public DealStatusChangedListener(NotificationService notificationService,
                                     CrmStatisticsService statisticsService) {
        this.notificationService = notificationService;
        this.statisticsService = statisticsService;
    }

    /**
     * TODO 4.8.2: Обработать событие DealStatusChangedEvent.
     *             Добавить @EventListener и @Async.
     *             Залогировать: "Сделка {} изменила статус: {} → {}"
     *             Вызвать notificationService.sendNotification() с сообщением об изменении.
     *             Вызвать statisticsService.recordDealStatusChanged(event.getNewStatus()).
     *
     * Почему @Async здесь важен:
     *  DealService.changeStatus() публикует событие синхронно — без @Async слушатель
     *  выполнится в том же потоке и замедлит ответ клиенту.
     */
    @EventListener
    @Async
    public void onDealStatusChanged(DealStatusChangedEvent event) {
        String info = "Сделка %s изменила статус: %s → %s".formatted(event.getDealId(), event.getOldStatus(), event.getNewStatus());
        log.info(info);
        notificationService.sendNotification(info);
        statisticsService.recordDealStatusChanged(event.getNewStatus());
    }
}
