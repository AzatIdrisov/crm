package com.crm.repository.spec;

import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Спецификации для динамической фильтрации сделок.
 *
 * Ключевые концепции:
 *  - Specification<T>: функциональный интерфейс, возвращающий javax.persistence.criteria.Predicate.
 *    Позволяет строить динамические запросы без JPQL и BooleanBuilder.
 *
 *  - Как работает: Specification принимает (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb)
 *    и возвращает Predicate. Spring Data JPA собирает их в WHERE через cb.and()/cb.or().
 *
 *  - Composability (компонуемость): specs объединяются через .and()/.or()/.not():
 *      Specification.where(hasStatus(NEW)).and(assignedTo(userId))
 *
 *  - Null-safe: если параметр null — возвращаем null. Spring Data JPA игнорирует
 *    null Specification, что позволяет строить опциональные фильтры.
 *
 *  - Использование с embedded-полем:
 *    root.get("amount").get("amount") — обращение к полю amount внутри Money.
 */
public class DealSpecification {

    private DealSpecification() {}

    /** Фильтр по статусу сделки. */
    public static Specification<Deal> hasStatus(DealStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    /** Фильтр по клиенту. */
    public static Specification<Deal> hasCustomer(Long customerId) {
        return (root, query, cb) ->
                customerId == null ? null : cb.equal(root.get("customer").get("id"), customerId);
    }

    /** Фильтр по ответственному менеджеру. */
    public static Specification<Deal> assignedTo(Long userId) {
        return (root, query, cb) ->
                userId == null ? null : cb.equal(root.get("assignedTo").get("id"), userId);
    }

    /** Фильтр: сумма сделки больше указанного значения. */
    public static Specification<Deal> amountGreaterThan(BigDecimal minAmount) {
        return (root, query, cb) ->
                minAmount == null ? null
                        : cb.greaterThan(root.get("amount").get("amount"), minAmount);
    }

    /** Фильтр: заголовок содержит строку (case-insensitive). */
    public static Specification<Deal> titleContains(String keyword) {
        return (root, query, cb) ->
                keyword == null || keyword.isBlank() ? null
                        : cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
    }
}
