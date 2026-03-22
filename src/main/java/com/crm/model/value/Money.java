package com.crm.model.value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

// Value Object для денежной суммы.
// Хранит сумму и валюту вместе — нельзя случайно сложить рубли с долларами.
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be negative: " + amount);
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency must not be null");
        }
        // Нормализуем масштаб: всегда 2 знака после запятой (100 → 100.00)
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    // Фабричный метод для удобного создания
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money ofRub(BigDecimal amount) {
        return of(amount, "RUB");
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Currency mismatch: %s vs %s".formatted(this.currency, other.currency)
            );
        }
    }

    @Override
    public String toString() {
        return amount + " " + currency.getCurrencyCode();
    }
}
