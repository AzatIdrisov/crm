package com.crm.repository;

import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.User;
import com.crm.model.enums.DealStatus;
import com.crm.model.enums.UserRole;
import com.crm.model.value.Money;
import com.crm.repository.projection.DealSummary;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Демонстрация проекций, @Query с JOIN FETCH и @Modifying запросов.
 *
 * Покрывает:
 *  1. Interface-based Projection (DealSummary) — только нужные колонки
 *  2. Nested projection (MoneyView) — для @Embedded полей
 *  3. @Query с JOIN FETCH — явная загрузка связей
 *  4. @Modifying + clearAutomatically — массовый UPDATE без загрузки объектов
 *  5. Проблема stale data без clearAutomatically
 */
class ProjectionAndModifyingTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    private User manager;
    private Customer customer;
    private Statistics stats;

    @BeforeEach
    void setUp() {
        manager  = userRepository.save(buildUser("proj@example.com", UserRole.MANAGER));
        customer = customerRepository.save(buildCustomer("proj-client@example.com"));

        for (int i = 1; i <= 3; i++) {
            dealRepository.save(Deal.builder()
                    .title("Deal " + i).status(DealStatus.NEW)
                    .amount(Money.ofRub(new BigDecimal(i * 10000)))
                    .customer(customer).assignedTo(manager).build());
        }
        entityManager.flush();
        entityManager.clear();

        stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
    }

    // -------------------------------------------------------------------------
    // Тест 1: Interface-based Projection — только нужные колонки
    // -------------------------------------------------------------------------

    @Test
    void projection_returnsOnlyRequestedFields() {
        // Spring Data видит возвращаемый тип DealSummary (interface) и генерирует
        // SELECT только запрошенных колонок — не загружает customer, assignedTo и пр.
        List<DealSummary> summaries = dealRepository.findAllProjectedBy();

        assertThat(summaries).hasSize(3);
        DealSummary first = summaries.get(0);

        assertThat(first.getId()).isNotNull();
        assertThat(first.getTitle()).startsWith("Deal");
        assertThat(first.getStatus()).isEqualTo(DealStatus.NEW);
    }

    @Test
    void projection_nestedView_accessesEmbeddedMoney() {
        // Вложенная проекция MoneyView: доступ к полям @Embedded Money
        List<DealSummary> summaries = dealRepository.findAllProjectedBy();

        summaries.forEach(s -> {
            assertThat(s.getAmount()).isNotNull();
            assertThat(s.getAmount().getAmount()).isPositive();
        });
    }

    @Test
    void projection_vs_fullEntity_fewerQueriesOrColumns() {
        // Проекция не загружает customer (LAZY) → не нужен JOIN
        // Сравниваем количество данных: проекция vs полная сущность
        stats.clear();
        List<DealSummary> projections = dealRepository.findAllProjectedBy();
        long projectionQueries = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        List<Deal> fullEntities = dealRepository.findAll();
        long entityQueries = stats.getPrepareStatementCount();

        // Оба варианта: 1 запрос (customer LAZY, не загружается)
        // Но проекция SELECT меньше колонок — меньше трафик между app и БД
        assertThat(projectionQueries).isEqualTo(1);
        assertThat(entityQueries).isEqualTo(1);

        // Реальный выигрыш проекций: не загружаем связанные объекты вообще
        // и не передаём ненужные колонки по сети
    }

    @Test
    void projectionWithFilter_byStatus() {
        dealRepository.save(Deal.builder()
                .title("Won Deal").status(DealStatus.WON)
                .amount(Money.ofRub(new BigDecimal("500000")))
                .customer(customer).assignedTo(manager).build());
        entityManager.flush();
        entityManager.clear();

        List<DealSummary> wonDeals = dealRepository.findProjectedByStatus(DealStatus.WON);
        assertThat(wonDeals).hasSize(1)
                .extracting(DealSummary::getTitle).containsExactly("Won Deal");
    }

    // -------------------------------------------------------------------------
    // Тест 2: @Query с JOIN FETCH — явная загрузка связей
    // -------------------------------------------------------------------------

    @Test
    void queryWithJoinFetch_loadsDetailsInOneSelect() {
        stats.clear();

        // findByIdWithDetails: LEFT JOIN FETCH customer LEFT JOIN FETCH assignedTo
        Deal deal = dealRepository.findByIdWithDetails(
                dealRepository.findAllProjectedBy().get(0).getId()
        ).orElseThrow();

        // Обращаемся к customer и assignedTo — данные уже в памяти, 0 доп. запросов
        String customerName = deal.getCustomer().getFirstName();
        String managerName  = deal.getAssignedTo().getFirstName();

        long queries = stats.getPrepareStatementCount();
        assertThat(queries).isEqualTo(1); // Только один JOIN SELECT
        assertThat(customerName).isNotBlank();
        assertThat(managerName).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // Тест 3: @Modifying — массовый UPDATE без загрузки объектов
    // -------------------------------------------------------------------------

    @Test
    void modifyingQuery_updatesStatusWithoutLoadingEntity() {
        Deal deal = dealRepository.findAll().get(0);
        Long id = deal.getId();

        stats.clear();

        // updateStatus: UPDATE deals SET status=?, version=version+1 WHERE id=?
        // НЕ загружает объект в память — только UPDATE
        int rowsUpdated = dealRepository.updateStatus(id, DealStatus.WON);

        assertThat(rowsUpdated).isEqualTo(1);

        entityManager.clear();
        Deal updated = dealRepository.findById(id).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DealStatus.WON);
    }

    @Test
    void modifyingQuery_clearAutomatically_preventsStaleData() {
        // clearAutomatically = true в @Modifying:
        // После UPDATE Hibernate очищает L1 кэш (persistence context).
        // Без этого: в памяти старый объект (status=NEW), в БД уже WON.
        // С этим: следующий findById пойдёт в БД и вернёт актуальный статус.

        Deal deal = dealRepository.findAll().get(0);
        Long id = deal.getId();

        // Загружаем объект (попадает в L1 кэш)
        Deal cached = dealRepository.findById(id).orElseThrow();
        assertThat(cached.getStatus()).isEqualTo(DealStatus.NEW);

        // Массовый UPDATE через @Modifying (clearAutomatically = true)
        dealRepository.updateStatus(id, DealStatus.WON);

        // clearAutomatically очистил кэш → следующий findById идёт в БД
        Deal fresh = dealRepository.findById(id).orElseThrow();
        assertThat(fresh.getStatus()).isEqualTo(DealStatus.WON); // актуальный статус!

        // БЕЗ clearAutomatically был бы такой баг:
        //   dealRepository.updateStatus(id, WON);          // UPDATE в БД
        //   Deal stale = dealRepository.findById(id).get(); // из L1 кэша!
        //   stale.getStatus() == NEW  ← НЕПРАВИЛЬНО, в БД уже WON
    }

    // -------------------------------------------------------------------------
    // Тест 4: Derived query count и exists — эффективные проверки
    // -------------------------------------------------------------------------

    @Test
    void countByStatus_selectCountNotObjects() {
        // countBy генерирует SELECT COUNT(*) — не загружает объекты
        stats.clear();

        long newCount = dealRepository.countByStatus(DealStatus.NEW);
        assertThat(newCount).isEqualTo(3);

        // Только 1 запрос: SELECT COUNT(*) FROM deals WHERE status = 'NEW'
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void findByAssignedUserAndStatus_customJpql() {
        // @Query с параметрами через @Param
        List<Deal> result = dealRepository.findByAssignedUserAndStatus(
                manager.getId(), DealStatus.NEW
        );
        assertThat(result).hasSize(3)
                .allSatisfy(d -> {
                    assertThat(d.getStatus()).isEqualTo(DealStatus.NEW);
                    assertThat(d.getAssignedTo().getId()).isEqualTo(manager.getId());
                });
    }
}
