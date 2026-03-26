package com.crm.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * В отличие от @UniqueEmail этот валидатор не обращается к БД —
 * только проверяет формат строки. Поэтому ValidPhoneValidator не нужен @Component.
 */
@Constraint(validatedBy = ValidPhoneValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ValidPhone {

    String message() default "Некорректный формат номера телефона";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
