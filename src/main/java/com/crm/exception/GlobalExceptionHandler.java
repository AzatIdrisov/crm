package com.crm.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Глобальная обработка исключений — перехватывает исключения из всех контроллеров.
 * <p>
 * Ключевые концепции:
 * - @RestControllerAdvice    — @ControllerAdvice + @ResponseBody (ответ сериализуется в JSON)
 * - @ExceptionHandler        — метод вызывается когда из контроллера вылетает указанный тип
 * - @ResponseStatus          — устанавливает HTTP-статус ответа
 * - MethodArgumentNotValid   — бросается при провале @Valid на @RequestBody
 * - ConstraintViolation      — бросается при провале @Validated на @PathVariable/@RequestParam
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 1. Поставить @ResponseStatus(HttpStatus.BAD_REQUEST)
     * 2. Собрать список ошибок из exception.getBindingResult().getFieldErrors():
     * каждая ошибка → строка "fieldName: message"
     * 3. Вернуть ErrorResponse.of(400, "Validation Failed", "Ошибки валидации", details)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        return ErrorResponse.of(400, "Validation Failed", "Ошибки валидации", details);
    }

    /**
     * 1. Поставить @ResponseStatus(HttpStatus.BAD_REQUEST)
     * 2. Собрать список ошибок из exception.getConstraintViolations():
     * каждое нарушение → строка violation.getPropertyPath() + ": " + violation.getMessage()
     * 3. Вернуть ErrorResponse.of(400, "Validation Failed", "Ошибки валидации", details)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return ErrorResponse.of(400, "Validation Failed", "Ошибки валидации", details);
    }

    /**
     * 1. Поставить @ResponseStatus(HttpStatus.NOT_FOUND)
     * 2. Вернуть ErrorResponse.of(404, "Not Found", ex.getMessage())
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleResourceNotFound(ResourceNotFoundException ex) {
        return ErrorResponse.of(404, "Not Found", ex.getMessage());
    }

    /**
     * 1. Поставить @ResponseStatus(HttpStatus.CONFLICT)
     * 2. Вернуть ErrorResponse.of(409, "Conflict", ex.getMessage())
     */
    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException ex) {
        return  ErrorResponse.of(409, "Conflict", ex.getMessage());
    }

    /**
     * 1. Поставить @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
     * 2. Залогировать исключение (добавь поле log — Logger через LoggerFactory)
     * 3. Вернуть ErrorResponse.of(500, "Internal Server Error", "Внутренняя ошибка сервера")
     * <p>
     * Почему нельзя возвращать ex.getMessage() клиенту:
     * Сообщение может содержать внутренние детали реализации (имена таблиц, SQL и т.п.)
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAll(Exception ex) {
        log.error("Необработанное исключение", ex);
        return  ErrorResponse.of(500, "Internal Server Error", "Внутренняя ошибка сервера");
    }
}
