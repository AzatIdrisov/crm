package com.crm.repository;

import com.crm.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий пользователей.
 *
 * Ключевые концепции:
 *  - JpaRepository<T, ID>: наследует CrudRepository + PagingAndSortingRepository.
 *    Из коробки: findById, findAll, save, delete, count, existsById и т.д.
 *
 *  - Derived query (производный запрос): Spring Data генерирует JPQL по имени метода.
 *    findByEmailValue → WHERE u.email.value = :value
 *    (email — @Embedded поле, value — компонент record Email)
 *
 *  - existsByEmailValue: SELECT COUNT(*) > 0 — эффективнее чем findBy + .isPresent()
 *    при проверке уникальности, т.к. не загружает объект.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // Обращение к @Embedded Email через компонент "value" record-а
    Optional<User> findByEmailValue(String emailValue);

    boolean existsByEmailValue(String emailValue);
}
