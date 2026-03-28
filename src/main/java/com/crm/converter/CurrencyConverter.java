package com.crm.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Currency;

/**
 * JPA-конвертер для java.util.Currency ↔ VARCHAR(3).
 *
 * Ключевые концепции:
 *  - AttributeConverter<X, Y>: X — Java-тип, Y — тип в БД
 *  - autoApply = true: конвертер применяется АВТОМАТИЧЕСКИ ко всем полям типа Currency
 *    во всех сущностях и @Embeddable (не нужно ставить @Convert на каждое поле)
 *
 * Зачем нужен:
 *  Hibernate не умеет маппить java.util.Currency из коробки.
 *  Храним валюту как код ISO 4217 (трёхбуквенная строка: "RUB", "USD", "EUR").
 */
@Converter(autoApply = true)
public class CurrencyConverter implements AttributeConverter<Currency, String> {

    @Override
    public String convertToDatabaseColumn(Currency currency) {
        return currency == null ? null : currency.getCurrencyCode();
    }

    @Override
    public Currency convertToEntityAttribute(String code) {
        return code == null ? null : Currency.getInstance(code);
    }
}
