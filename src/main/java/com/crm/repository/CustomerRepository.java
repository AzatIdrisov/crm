package com.crm.repository;

import com.crm.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Репозиторий клиентов.
 *
 * Ключевые концепции:
 *  - JpaSpecificationExecutor<T>: добавляет методы findAll(Specification),
 *    findOne(Specification), count(Specification), exists(Specification).
 *    Используется совместно с DealSpecification-подобными классами для
 *    динамической фильтрации.
 *
 *  - Derived query для @Embedded поля: findByEmailValue обращается к
 *    Customer.email.value (Email — embedded record с компонентом "value").
 *    Spring Data генерирует: WHERE c.email.value = :emailValue
 *
 *  - existsByEmailValue: используется в UniqueEmailValidator.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByEmailValue(String emailValue);

    boolean existsByEmailValue(String emailValue);
}
