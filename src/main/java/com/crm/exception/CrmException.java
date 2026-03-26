package com.crm.exception;

/**
 * Базовое исключение CRM — все доменные исключения наследуются от него.
 * RuntimeException — не требует обязательного перехвата (unchecked).
 */
public class CrmException extends RuntimeException {

    public CrmException(String message) {
        super(message);
    }

    public CrmException(String message, Throwable cause) {
        super(message, cause);
    }
}
