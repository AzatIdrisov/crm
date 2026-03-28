package com.crm.model;

import com.crm.model.enums.UserRole;
import com.crm.model.value.Email;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * JPA-сущность пользователя системы.
 *
 * Ключевые концепции:
 *  - @Table(uniqueConstraints): уникальность email объявляется на уровне DDL
 *    (Liquibase создаёт реальный UNIQUE INDEX). JPA-аннотация служит документацией
 *    и используется при ddl-auto=create/update.
 *
 *  - @Embedded + @AttributeOverride: Email — @Embeddable record с компонентом "value".
 *    По умолчанию Hibernate создал бы колонку "value", что неинформативно.
 *    @AttributeOverride переименовывает её в "email".
 *
 *  - @Enumerated(STRING): хранить enum как строку, не как int-индекс.
 *    Если хранить как ORDINAL (дефолт) и потом вставить новый статус в середину
 *    enum — все значения в БД сломаются.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email")
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class User extends BaseEntity<Long> {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    // Email-record встраивается как одна колонка "email" (переопределяем дефолтное "value")
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email", nullable = false, length = 255))
    private Email email;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
