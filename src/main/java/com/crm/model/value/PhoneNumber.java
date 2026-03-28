package com.crm.model.value;

import jakarta.persistence.Embeddable;

// Value Object для номера телефона.
// Принимает форматы: +7 999 123-45-67, 8(999)1234567, +1-800-555-0199 и т.д.
@Embeddable
public record PhoneNumber(String value) {

    private static final String PHONE_REGEX = "^\\+?[\\d\\s\\-().]{7,20}$";

    public PhoneNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Phone number must not be blank");
        }
        if (!value.matches(PHONE_REGEX)) {
            throw new IllegalArgumentException("Invalid phone number format: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
