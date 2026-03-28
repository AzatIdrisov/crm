package com.crm.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Базовый маппируемый суперкласс для всех JPA-сущностей.
 *
 * Ключевые концепции:
 *  - @MappedSuperclass: поля класса включаются в таблицы наследников,
 *    но сам класс НЕ является сущностью и не имеет своей таблицы.
 *    Отличие от @Inheritance: здесь нет единой таблицы на иерархию.
 *
 *  - @Id + @GeneratedValue(IDENTITY): Hibernate резолвит generic-тип ID
 *    через параметр <ID> конкретного наследника (Customer extends BaseEntity<Long>).
 *    IDENTITY — PostgreSQL BIGSERIAL/SERIAL, auto-increment на стороне БД.
 *
 *  - @EntityListeners(AuditingEntityListener.class): Spring Data JPA слушает
 *    события persist/update и автоматически ставит createdAt/updatedAt.
 *    Требует @EnableJpaAuditing в конфигурации.
 *
 *  - @CreatedDate/@LastModifiedDate: заполняются ОДИН раз (createdAt) и при
 *    каждом обновлении (updatedAt). @Column(updatable = false) защищает createdAt
 *    от случайного перезатирания при UPDATE.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity<ID> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private ID id;

    // updatable = false: поле устанавливается при INSERT и больше не меняется
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
