package com.crm.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Generic-базовый класс для всех сущностей.
// Параметр ID позволяет гибко задавать тип первичного ключа (Long, UUID и т.д.)
@Getter
@Setter
public abstract class BaseEntity<ID> {

    private ID id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
