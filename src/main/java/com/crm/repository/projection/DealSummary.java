package com.crm.repository.projection;

import com.crm.model.enums.DealStatus;

import java.math.BigDecimal;

/**
 * Интерфейсная проекция для краткой информации о сделке.
 *
 * Ключевые концепции:
 *  - Проекции (Projections): позволяют получить только нужные колонки из БД,
 *    не загружая всю сущность. Это уменьшает трафик между приложением и БД.
 *
 *  - Interface-based projection: Spring Data JPA генерирует прокси, реализующий
 *    этот интерфейс. Имена методов должны совпадать с именами полей сущности
 *    (getTitle → title, getStatus → status).
 *
 *  - Для вложенных/embedded полей используется нотация через вложенные интерфейсы:
 *    getAmount().getAmount() — обращение к Money.amount через отдельный интерфейс.
 *
 *  - Альтернатива: DTO-проекция (record/class с @Value или конструктором в @Query).
 *    Она чуть быстрее (нет прокси), но менее гибкая.
 */
public interface DealSummary {

    Long getId();

    String getTitle();

    DealStatus getStatus();

    // Вложенная проекция для @Embedded Money
    MoneyView getAmount();

    interface MoneyView {
        BigDecimal getAmount();
        String getCurrency(); // Currency конвертируется CurrencyConverter → String
    }
}
