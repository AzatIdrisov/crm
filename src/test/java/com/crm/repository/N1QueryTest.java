package com.crm.repository;

import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.User;
import com.crm.model.enums.DealStatus;
import com.crm.model.enums.UserRole;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Демонстрация проблемы N+1 и способов её решения.
 *
 * N+1 — одна из самых частых проблем производительности в JPA-приложениях.
 *
 * Сценарий:
 *  Загружаем N сделок, затем для каждой обращаемся к связанному Customer.
 *  Без JOIN FETCH: 1 SELECT (deals) + N SELECT (customer для каждой уникальной сделки)
 *  С JOIN FETCH:   1 SELECT с LEFT JOIN — нет дополнительных запросов
 *
 * @TestPropertySource: отключаем default_batch_fetch_size=25 (из application.yml),
 * чтобы продемонстрировать "чистый" N+1 без оптимизации пакетной загрузки.
 * С batch_fetch_size=25 Hibernate заменил бы N отдельных SELECT на один
 * SELECT ... WHERE id IN (1, 2, 3, ...) — частичная оптимизация, но не полная.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=1")
class N1QueryTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    private Statistics stats;

    @BeforeEach
    void setUp() {
        // Hibernate Statistics: счётчик всех PreparedStatement.execute() вызовов
        // Позволяет точно посчитать количество SQL-запросов в тесте.
        stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
    }

    // -------------------------------------------------------------------------
    // Подготовка тестовых данных: 3 клиента, у каждого по 2 сделки
    // -------------------------------------------------------------------------

    private void seedDeals() {
        User manager = userRepository.save(buildUser("n1@example.com", UserRole.MANAGER));

        for (int i = 1; i <= 3; i++) {
            Customer customer = customerRepository.save(buildCustomer("client" + i + "@example.com"));
            dealRepository.save(buildDeal("Deal " + i + "A", DealStatus.NEW, customer, manager));
            dealRepository.save(buildDeal("Deal " + i + "B", DealStatus.WON, customer, manager));
        }
        entityManager.flush();
        // Очищаем L1 кэш (Persistence Context) — иначе Hibernate вернёт объекты
        // прямо из кэша, не обращаясь к БД, и N+1 не проявится
        entityManager.clear();
    }

    // -------------------------------------------------------------------------
    // Тест 1: Демонстрация N+1 (ПЛОХО)
    // -------------------------------------------------------------------------

    @Test
    void nPlusOne_findAll_thenAccessCustomer_causesExtraQueries() {
        seedDeals();
        stats.clear(); // сбрасываем счётчик ПОСЛЕ заполнения данных

        // 1 SELECT: SELECT * FROM deals
        List<Deal> deals = dealRepository.findAll();
        assertThat(deals).hasSize(6);

        // На этом этапе: 1 запрос (только deals, customer — LAZY proxy, не загружен)
        long queriesAfterLoad = stats.getPrepareStatementCount();
        assertThat(queriesAfterLoad).isEqualTo(1);

        // Обращаемся к customer для каждой сделки
        // Для каждого УНИКАЛЬНОГО customer.id Hibernate делает отдельный SELECT
        // (один и тот же customer не загружается дважды — L1 кэш работает в рамках сессии)
        // 3 клиента × 2 сделки каждый → 3 дополнительных SELECT customer
        deals.forEach(deal -> {
            String name = deal.getCustomer().getFirstName(); // LAZY LOAD → SELECT customer WHERE id = ?
        });

        long totalQueries = stats.getPrepareStatementCount();
        // Итого: 1 (deals) + 3 (customers) = 4 запроса
        // Без default_batch_fetch_size=1 это было бы 1+1 = 2 (batch IN query)
        assertThat(totalQueries).isEqualTo(4); // 1 + N (3 уникальных клиента)

        System.out.printf("""
                [N+1] Запросов: %d (1 для deals + %d для customers)%n
                Представьте 1000 сделок с 500 уникальными клиентами = 501 запрос!%n
                """, totalQueries, totalQueries - 1);
    }

    // -------------------------------------------------------------------------
    // Тест 2: Решение через JOIN FETCH (ХОРОШО)
    // -------------------------------------------------------------------------

    @Test
    void joinFetch_loadsAllInOneQuery() {
        seedDeals();
        stats.clear();

        // JOIN FETCH: LEFT JOIN deals d ON d.customer_id = c.id
        // Загружает и сделки и клиентов ОДНИМ запросом
        List<Deal> deals = dealRepository.findAllWithDetails();
        assertThat(deals).hasSize(6);

        // Обращаемся к customer — данные уже в памяти, никаких дополнительных SELECT
        deals.forEach(deal -> {
            String name = deal.getCustomer().getFirstName(); // данные уже загружены!
        });

        long totalQueries = stats.getPrepareStatementCount();
        // Ровно 1 запрос — независимо от количества сделок и клиентов
        assertThat(totalQueries).isEqualTo(1);

        System.out.printf("[JOIN FETCH] Запросов: %d (всегда 1, независимо от N)%n", totalQueries);
    }

    // -------------------------------------------------------------------------
    // Тест 3: L1 кэш работает — одинаковый объект не грузится дважды
    // -------------------------------------------------------------------------

    @Test
    void firstLevelCache_sameCustomer_loadedOnlyOnce() {
        seedDeals();

        // Дополнительно: грузим ещё одного клиента — у него много сделок
        Customer popular = customerRepository.save(buildCustomer("popular@example.com"));
        User manager = userRepository.findByEmailValue("n1@example.com").orElseThrow();
        for (int i = 0; i < 5; i++) {
            dealRepository.save(buildDeal("Pop Deal " + i, DealStatus.NEW, popular, manager));
        }
        entityManager.flush();
        entityManager.clear();
        stats.clear();

        // Загружаем все 11 сделок (6 + 5 новых)
        List<Deal> deals = dealRepository.findAll();

        // Обращаемся к customer для каждой сделки (5 сделок с popular клиентом)
        deals.forEach(d -> d.getCustomer().getFirstName());

        long queries = stats.getPrepareStatementCount();
        // 1 (deals) + 4 (3 старых + 1 popular) = 5, а не 1 + 11
        // L1 кэш: popular загружен один раз, следующие 4 обращения — из кэша
        assertThat(queries).isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // Тест 4: assignedTo тоже LAZY — отдельная серия N+1 без JOIN FETCH
    // -------------------------------------------------------------------------

    @Test
    void multipleAssociations_withoutFetch_multipleNPlusOne() {
        seedDeals();
        stats.clear();

        List<Deal> deals = dealRepository.findAll();
        // 1 запрос (deals)
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);

        // Обращаемся к customer (3 уникальных)
        deals.forEach(d -> d.getCustomer().getFirstName());
        assertThat(stats.getPrepareStatementCount()).isEqualTo(4); // 1 + 3

        // Обращаемся к assignedTo (1 уникальный manager)
        deals.forEach(d -> d.getAssignedTo().getFirstName());
        assertThat(stats.getPrepareStatementCount()).isEqualTo(5); // 4 + 1

        // Итого: 5 запросов вместо 1
        // Каждая LAZY-ассоциация — потенциальный источник N+1
    }
}
