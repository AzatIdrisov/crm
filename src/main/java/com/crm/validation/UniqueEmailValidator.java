package com.crm.validation;

import com.crm.model.value.Email;
import com.crm.service.CustomerService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

/**
 * Валидатор для @UniqueEmail — проверяет, что email ещё не зарегистрирован.
 *
 * Ключевые концепции:
 *  - ConstraintValidator<A, T>: A — аннотация, T — тип валидируемого поля
 *  - initialize(annotation) — вызывается один раз при создании валидатора
 *  - isValid(value, context) — вызывается при каждой валидации
 *  - Spring внедряет зависимости в валидатор через @Component
 */
@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {

    // Spring-бин — внедряется через конструктор, не через @Autowired
    private final CustomerService customerService;

    public UniqueEmailValidator(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public void initialize(UniqueEmail annotation) {
    }

    /**
     * TODO 4.4.3: Реализовать isValid().
     *
     *  Логика:
     *   1. Если value == null или пустая строка — вернуть true
     *      (null-проверка — задача @NotNull/@NotBlank, не наша)
     *   2. Создать Email value object: new Email(value)
     *   3. Вернуть true если customerService.findByEmail(email).isEmpty()
     *
     *  Почему null → true:
     *   Валидаторы не должны дублировать ответственность других аннотаций.
     *   @NotBlank уже запретит null/пустую строку — если оба стоят на поле.
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        try {
            return customerService.findByEmail(new Email(value)).isEmpty();
        } catch (IllegalArgumentException e) {
            // Невалидный формат email — не наша ответственность, её проверяет @Email.
            // Возвращаем true чтобы не генерировать двойную ошибку и не бросать 500.
            return true;
        }
    }
}
