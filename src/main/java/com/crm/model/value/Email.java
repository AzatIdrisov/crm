package com.crm.model.value;

// Value Object для email-адреса.
// Record — иммутабельный, equals/hashCode/toString генерируются автоматически.
public record Email(String value) {

    private static final String EMAIL_REGEX = "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$";

    // Компактный конструктор record'а — валидация при создании
    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (!value.matches(EMAIL_REGEX)) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
