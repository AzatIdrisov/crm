package com.crm.repository;

import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import com.crm.repository.projection.DealSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий сделок.
 *
 * Демонстрирует все основные возможности Spring Data JPA:
 *  - Derived queries (findByStatus, findByCustomerId)
 *  - JPQL @Query с параметрами
 *  - JOIN FETCH для решения N+1
 *  - @Modifying для UPDATE/DELETE запросов
 *  - Projections (DealSummary)
 *  - JpaSpecificationExecutor для динамической фильтрации
 */
public interface DealRepository extends JpaRepository<Deal, Long>, JpaSpecificationExecutor<Deal> {

    // ----- Derived queries -----

    List<Deal> findByStatus(DealStatus status);

    List<Deal> findByCustomerId(Long customerId);

    List<Deal> findByAssignedToId(Long userId);

    long countByStatus(DealStatus status);

    // ----- @Query JPQL — JOIN FETCH (решение N+1) -----

    /**
     * Загрузить сделку со всеми связями за ONE SQL-запрос (JOIN FETCH).
     *
     * Без JOIN FETCH: загружается Deal, затем отдельные SELECT для customer и assignedTo.
     * С JOIN FETCH: один SELECT с LEFT JOIN — нет дополнительных запросов.
     *
     * Нюанс: нельзя использовать несколько JOIN FETCH для коллекций (tasks, comments)
     * одновременно — Hibernate бросит MultipleBagFetchException. Решение:
     *  - для коллекций использовать отдельные запросы или @EntityGraph
     */
    @Query("""
            SELECT d FROM Deal d
            LEFT JOIN FETCH d.customer
            LEFT JOIN FETCH d.assignedTo
            WHERE d.id = :id
            """)
    Optional<Deal> findByIdWithDetails(@Param("id") Long id);

    /**
     * Загрузить ВСЕ сделки с customer и assignedTo за ONE SQL-запрос.
     * Используется в тестах для демонстрации решения N+1 через JOIN FETCH.
     */
    @Query("""
            SELECT d FROM Deal d
            LEFT JOIN FETCH d.customer
            LEFT JOIN FETCH d.assignedTo
            """)
    List<Deal> findAllWithDetails();

    /**
     * Фильтр по ответственному и статусу — параметры через @Param.
     */
    @Query("""
            SELECT d FROM Deal d
            WHERE d.assignedTo.id = :userId
              AND d.status = :status
            """)
    List<Deal> findByAssignedUserAndStatus(
            @Param("userId") Long userId,
            @Param("status") DealStatus status
    );

    // ----- @Modifying — UPDATE без загрузки объекта в память -----

    /**
     * Массовое обновление статуса без загрузки сущностей.
     *
     * Ключевые концепции:
     *  - @Modifying: обязателен для DML-запросов (UPDATE, DELETE, INSERT).
     *    Без него Spring Data бросает InvalidDataAccessApiUsageException.
     *
     *  - clearAutomatically = true: после UPDATE Hibernate очищает кэш первого уровня
     *    (persistence context). Без этого кэшированные объекты в памяти будут иметь
     *    старый статус, хотя в БД уже новый — это очень опасный баг.
     *
     *  - @Transactional здесь не нужен — транзакция должна быть открыта в сервисе.
     *    Но для явности можно добавить.
     *
     *  - Возвращает int — количество обновлённых строк.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Deal d SET d.status = :status WHERE d.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") DealStatus status);

    // ----- Projection -----

    /**
     * Получить краткую информацию о всех сделках (без загрузки связей).
     *
     * Spring Data видит, что возвращаемый тип — интерфейс (DealSummary),
     * и генерирует SELECT только нужных колонок, не загружая связанные сущности.
     */
    List<DealSummary> findAllProjectedBy();

    /**
     * Проекция с фильтром по статусу.
     */
    List<DealSummary> findProjectedByStatus(DealStatus status);
}
