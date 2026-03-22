package com.crm.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

// Generic-базовый класс для всех сущностей.
// Параметр ID позволяет гибко задавать тип первичного ключа (Long, UUID и т.д.)
// @SuperBuilder позволяет наследникам включать поля родителя в свой билдер
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseEntity<ID> {

    private ID id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
