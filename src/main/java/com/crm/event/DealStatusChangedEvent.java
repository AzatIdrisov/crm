package com.crm.event;

import com.crm.model.enums.DealStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Событие изменения статуса сделки.
 *
 * Ключевые концепции:
 *  - ApplicationEvent       — базовый класс Spring-событий
 *  - source                 — объект, опубликовавший событие (обычно сам сервис)
 *  - Событие — immutable:   все данные передаются через конструктор, сеттеров нет
 *
 * TODO 4.8.1: Расширить ApplicationEvent.
 *             Добавить поля: dealId (Long), oldStatus (DealStatus), newStatus (DealStatus).
 *             Создать конструктор: super(source) + присвоение полей.
 *             Поля — final, геттеры через IDE или Lombok @Getter (без @Setter — событие immutable).
 */
@Getter
public class DealStatusChangedEvent extends ApplicationEvent {

    // TODO: добавить поля dealId, oldStatus, newStatus
    private final Long dealId;
    private final DealStatus oldStatus;
    private final DealStatus newStatus;

    public DealStatusChangedEvent(Object source, Long dealId, DealStatus oldStatus, DealStatus newStatus) {
        super(source);
        this.dealId = dealId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

}
