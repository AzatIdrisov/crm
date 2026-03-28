package com.crm.repository;

import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.User;
import com.crm.model.enums.DealStatus;
import com.crm.model.enums.UserRole;
import com.crm.model.value.Money;
import com.crm.repository.spec.DealSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Демонстрация Specification для динамической фильтрации.
 *
 * Проблема без Specification:
 *   Для 5 фильтров нужно 2^5 = 32 метода в репозитории, чтобы покрыть все комбинации.
 *   С каждым новым фильтром количество методов удваивается.
 *
 * Решение: Specification<T> — компонуемые предикаты.
 *   Каждый критерий = отдельная Specification.
 *   Null-safe: если параметр null → Specification возвращает null → Spring Data игнорирует.
 *   Composability: specs объединяются через .and() / .or() / .not().
 */
class SpecificationTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    private User manager1;
    private User manager2;
    private Customer customerA;
    private Customer customerB;
    private Deal dealNew1;     // NEW, 50000, customerA, manager1
    private Deal dealNew2;     // NEW, 200000, customerB, manager2
    private Deal dealWon;      // WON, 300000, customerA, manager1
    private Deal dealLost;     // LOST, 10000, customerB, manager2

    @BeforeEach
    void setUp() {
        manager1  = userRepository.save(buildUser("spec-m1@example.com", UserRole.MANAGER));
        manager2  = userRepository.save(buildUser("spec-m2@example.com", UserRole.MANAGER));
        customerA = customerRepository.save(buildCustomer("spec-a@example.com"));
        customerB = customerRepository.save(buildCustomer("spec-b@example.com"));

        dealNew1 = dealRepository.save(Deal.builder()
                .title("Small New Deal").status(DealStatus.NEW)
                .amount(Money.ofRub(new BigDecimal("50000")))
                .customer(customerA).assignedTo(manager1).build());

        dealNew2 = dealRepository.save(Deal.builder()
                .title("Big New Deal").status(DealStatus.NEW)
                .amount(Money.ofRub(new BigDecimal("200000")))
                .customer(customerB).assignedTo(manager2).build());

        dealWon = dealRepository.save(Deal.builder()
                .title("Won Deal").status(DealStatus.WON)
                .amount(Money.ofRub(new BigDecimal("300000")))
                .customer(customerA).assignedTo(manager1).build());

        dealLost = dealRepository.save(Deal.builder()
                .title("Lost Deal").status(DealStatus.LOST)
                .amount(Money.ofRub(new BigDecimal("10000")))
                .customer(customerB).assignedTo(manager2).build());

        entityManager.flush();
        entityManager.clear();
    }

    // -------------------------------------------------------------------------
    // Одиночные спецификации
    // -------------------------------------------------------------------------

    @Test
    void hasStatus_filtersCorrectly() {
        List<Deal> newDeals = dealRepository.findAll(
                Specification.where(DealSpecification.hasStatus(DealStatus.NEW))
        );
        assertThat(newDeals).hasSize(2)
                .extracting(Deal::getStatus)
                .containsOnly(DealStatus.NEW);
    }

    @Test
    void hasCustomer_filtersCorrectly() {
        List<Deal> dealsForA = dealRepository.findAll(
                Specification.where(DealSpecification.hasCustomer(customerA.getId()))
        );
        assertThat(dealsForA).hasSize(2)
                .extracting(d -> d.getCustomer().getId())
                .containsOnly(customerA.getId());
    }

    @Test
    void assignedTo_filtersCorrectly() {
        List<Deal> manager1Deals = dealRepository.findAll(
                Specification.where(DealSpecification.assignedTo(manager1.getId()))
        );
        assertThat(manager1Deals).hasSize(2)
                .extracting(d -> d.getAssignedTo().getId())
                .containsOnly(manager1.getId());
    }

    @Test
    void amountGreaterThan_filtersCorrectly() {
        List<Deal> bigDeals = dealRepository.findAll(
                Specification.where(DealSpecification.amountGreaterThan(new BigDecimal("100000")))
        );
        // dealNew2 (200000) и dealWon (300000) проходят фильтр
        assertThat(bigDeals).hasSize(2)
                .extracting(d -> d.getAmount().amount())
                .allSatisfy(amt -> assertThat(amt).isGreaterThan(new BigDecimal("100000")));
    }

    @Test
    void titleContains_caseInsensitive() {
        List<Deal> dealsWithBig = dealRepository.findAll(
                Specification.where(DealSpecification.titleContains("big"))
        );
        assertThat(dealsWithBig).hasSize(1)
                .extracting(Deal::getTitle).containsExactly("Big New Deal");
    }

    // -------------------------------------------------------------------------
    // Компонуемые спецификации: .and() / .or()
    // -------------------------------------------------------------------------

    @Test
    void combined_statusAndCustomer_narrowsResults() {
        // Сделки клиента A со статусом NEW — только dealNew1
        Specification<Deal> spec = Specification
                .where(DealSpecification.hasStatus(DealStatus.NEW))
                .and(DealSpecification.hasCustomer(customerA.getId()));

        List<Deal> result = dealRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(Deal::getId).containsExactly(dealNew1.getId());
    }

    @Test
    void combined_threeFilters_preciseFilter() {
        // Сделки manager1 + статус WON + сумма > 100000
        Specification<Deal> spec = Specification
                .where(DealSpecification.assignedTo(manager1.getId()))
                .and(DealSpecification.hasStatus(DealStatus.WON))
                .and(DealSpecification.amountGreaterThan(new BigDecimal("100000")));

        List<Deal> result = dealRepository.findAll(spec);
        assertThat(result).hasSize(1)
                .extracting(Deal::getId).containsExactly(dealWon.getId());
    }

    @Test
    void combined_orCondition() {
        // Сделки со статусом WON ИЛИ LOST
        Specification<Deal> spec = Specification
                .where(DealSpecification.hasStatus(DealStatus.WON))
                .or(DealSpecification.hasStatus(DealStatus.LOST));

        List<Deal> result = dealRepository.findAll(spec);
        assertThat(result).hasSize(2)
                .extracting(Deal::getStatus)
                .containsExactlyInAnyOrder(DealStatus.WON, DealStatus.LOST);
    }

    // -------------------------------------------------------------------------
    // Null-safe спецификации: null-параметр игнорируется
    // -------------------------------------------------------------------------

    @Test
    void nullSpec_ignored_returnsAll() {
        // Ключевое свойство: null Specification → Spring Data игнорирует критерий.
        // Это позволяет писать универсальные методы поиска:
        //   service.search(status=null, customerId=null, ...) → вернёт все записи

        Specification<Deal> spec = Specification
                .where(DealSpecification.hasStatus(null))      // null → игнорируется
                .and(DealSpecification.hasCustomer(null))       // null → игнорируется
                .and(DealSpecification.amountGreaterThan(null)); // null → игнорируется

        List<Deal> result = dealRepository.findAll(spec);
        // Все фильтры null → возвращаются все 4 сделки
        assertThat(result).hasSize(4);
    }

    @Test
    void partialNullSpec_appliesOnlyNonNullCriteria() {
        // status задан, остальное null → фильтруем только по статусу
        Specification<Deal> spec = Specification
                .where(DealSpecification.hasStatus(DealStatus.NEW))
                .and(DealSpecification.hasCustomer(null))
                .and(DealSpecification.amountGreaterThan(null));

        List<Deal> result = dealRepository.findAll(spec);
        assertThat(result).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // Спецификация с доступом к embedded полю
    // -------------------------------------------------------------------------

    @Test
    void amountGreaterThan_accessesEmbeddedMoney() {
        // root.get("amount").get("amount") — обращение к Money.amount через embedded
        // Hibernate генерирует: WHERE d.amount > ?
        List<Deal> expensive = dealRepository.findAll(
                Specification.where(DealSpecification.amountGreaterThan(new BigDecimal("199999")))
        );
        assertThat(expensive).hasSize(2)
                .extracting(Deal::getTitle)
                .containsExactlyInAnyOrder("Big New Deal", "Won Deal");
    }
}
