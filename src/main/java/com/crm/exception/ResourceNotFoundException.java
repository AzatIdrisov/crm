package com.crm.exception;

public class ResourceNotFoundException extends CrmException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super("%s с id=%s не найден".formatted(resourceName, id));
    }
}
