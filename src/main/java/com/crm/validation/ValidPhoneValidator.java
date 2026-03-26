package com.crm.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Валидатор для @ValidPhone — проверяет формат номера телефона.
 *
 * В отличие от UniqueEmailValidator:
 *  - Не обращается к БД, не требует Spring-бинов
 *  - Не нужен @Component — Bean Validation создаёт его сам через reflection
 */
public class ValidPhoneValidator implements ConstraintValidator<ValidPhone, String> {

    /**
     *
     *  Правила формата (все необязательные части в скобках):
     *   - Может начинаться с "+" и кода страны (1–3 цифры)
     *   - Допустимые разделители: пробел, дефис, точка
     *   - Допустимо обёртывание части номера в скобки: (495)
     *   - Итоговая длина цифр: от 7 до 15
     *
     *  Пример регулярного выражения:
     *   "^\\+?[\\d\\s\\-().]{7,20}$"  — упрощённый, принимает большинство форматов
     *
     *  Создать как: private static final Pattern PHONE_PATTERN = Pattern.compile("...");
     *  Почему static final: паттерн компилируется один раз, не при каждой валидации.
     */

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[\\d\\s\\-().]{7,20}$");

    /**
     *
     *  Логика:
     *   1. Если value == null — вернуть true (null — задача @NotNull)
     *   2. Убрать все пробелы из строки для нормализации перед проверкой
     *   3. Вернуть PHONE_PATTERN.matcher(value).matches()
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        value = value.replace(" ", "");
        return PHONE_PATTERN.matcher(value).matches();
    }
}
