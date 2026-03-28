package com.crm.model.value;

import jakarta.persistence.Embeddable;

// Value Object для email-адреса.
// @Embeddable — JPA может встроить этот тип в родительскую таблицу как набор колонок.
// Hibernate 6.2+ поддерживает Records как @Embeddable: использует канонический конструктор
// для гидрации объекта из ResultSet (поэтому setter-ы не нужны).
@Embeddable
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
