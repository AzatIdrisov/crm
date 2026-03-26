package com.crm.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TODO 4.4.1: Оформить как аннотацию валидации.
 *
 * Что нужно добавить:
 *  1. @Target — разрешить применение только на FIELD
 *  2. @Retention(RUNTIME) — аннотация должна быть видна в рантайме
 *  3. @Constraint(validatedBy = UniqueEmailValidator.class) — связать с валидатором
 *  4. Три обязательных атрибута любого @Constraint:
 *       String message() default "...";
 *       Class<?>[] groups() default {};
 *       Class<? extends Payload>[] payload() default {};
 *
 * Пример использования после реализации:
 *   @UniqueEmail
 *   private String email;
 */
@Constraint(validatedBy = UniqueEmailValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UniqueEmail {

    String message() default "Email уже используется";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
