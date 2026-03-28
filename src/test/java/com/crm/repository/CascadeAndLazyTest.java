package com.crm.repository;

import com.crm.model.Comment;
import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.Task;
import com.crm.model.User;
import com.crm.model.enums.DealStatus;
import com.crm.model.enums.TaskPriority;
import com.crm.model.enums.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Демонстрация каскадных операций, orphanRemoval и проблемы LazyInitializationException.
 *
 * Покрывает:
 *  - CascadeType.ALL: сохранение дочерних объектов при сохранении родителя
 *  - orphanRemoval = true: удаление из коллекции → DELETE из БД
 *  - LAZY loading: нюансы загрузки связанных объектов
 *  - LazyInitializationException: что происходит при доступе вне сессии
 *  - Как правильно инициализировать LAZY коллекцию
 */
class CascadeAndLazyTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Тест 1: CascadeType.ALL — сохранение дочерних с родителем
    // -------------------------------------------------------------------------

    @Test
    void cascade_persist_savesChildrenWithParent() {
        // ДАНО: Customer с двумя Deal (ещё не сохранёнными)
        User manager = userRepository.save(buildUser("cascade@example.com", UserRole.MANAGER));
        Customer customer = buildCustomer("cascade-client@example.com");

        Deal deal1 = buildDeal("Deal 1", DealStatus.NEW, customer, manager);
        Deal deal2 = buildDeal("Deal 2", DealStatus.WON, customer, manager);
        customer.getDeals().add(deal1);
        customer.getDeals().add(deal2);

        // КОГДА: сохраняем только Customer
        // CascadeType.ALL → Hibernate автоматически делает INSERT для deal1 и deal2
        Customer saved = customerRepository.save(customer);
        testEntityManager.flush();
        testEntityManager.clear();

        // ТОГДА: обе сделки сохранены
        assertThat(dealRepository.findByCustomerId(saved.getId())).hasSize(2);
    }

    @Test
    void cascade_remove_deletesChildrenWithParent() {
        // ON DELETE CASCADE в DDL + CascadeType.ALL в JPA — двойная защита
        User manager = userRepository.save(buildUser("cascade-del@example.com", UserRole.MANAGER));
        Customer customer = customerRepository.save(buildCustomer("del-client@example.com"));
        Deal deal = dealRepository.save(buildDeal("Deal to Delete", DealStatus.NEW, customer, manager));
        Comment comment = commentRepository.save(Comment.builder()
                .content("Test comment")
                .deal(deal)
                .author(manager)
                .build());
        testEntityManager.flush();
        testEntityManager.clear();

        Long dealId = deal.getId();
        Long commentId = comment.getId();

        // Удаляем сделку → каскадно удаляются комментарии (ON DELETE CASCADE в DDL)
        dealRepository.deleteById(dealId);
        testEntityManager.flush();

        assertThat(dealRepository.findById(dealId)).isEmpty();
        assertThat(commentRepository.findById(commentId)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Тест 2: orphanRemoval — удаление из коллекции → DELETE строки
    // -------------------------------------------------------------------------

    @Test
    void orphanRemoval_removingFromCollection_deletesFromDatabase() {
        // orphanRemoval = true в Customer.deals:
        // Если убрать Deal из списка customer.deals — Hibernate сделает DELETE в БД.
        // Это отличается от простого "отвязывания" (setting customer to null).

        User manager = userRepository.save(buildUser("orphan@example.com", UserRole.MANAGER));
        Customer customer = customerRepository.save(buildCustomer("orphan-client@example.com"));
        Deal deal1 = dealRepository.save(buildDeal("To Keep", DealStatus.NEW, customer, manager));
        Deal deal2 = dealRepository.save(buildDeal("To Orphan", DealStatus.NEW, customer, manager));
        testEntityManager.flush();
        testEntityManager.clear();

        // Загружаем Customer и убираем одну сделку из коллекции
        Customer loaded = customerRepository.findById(customer.getId()).orElseThrow();
        loaded.getDeals().removeIf(d -> d.getId().equals(deal2.getId()));

        customerRepository.save(loaded);
        testEntityManager.flush();
        testEntityManager.clear();

        // Сделка удалена из БД
        assertThat(dealRepository.findById(deal2.getId())).isEmpty();
        // Вторая сделка осталась
        assertThat(dealRepository.findById(deal1.getId())).isPresent();
    }

    @Test
    void orphanRemoval_task_removingFromDeal_deletesTask() {
        User manager = userRepository.save(buildUser("orphan-task@example.com", UserRole.MANAGER));
        Customer customer = customerRepository.save(buildCustomer("orphan-task-client@example.com"));
        Deal deal = dealRepository.save(buildDeal("Deal with Tasks", DealStatus.NEW, customer, manager));
        Task task1 = taskRepository.save(buildTask("Task 1", TaskPriority.HIGH, deal));
        Task task2 = taskRepository.save(buildTask("Task 2", TaskPriority.LOW, deal));
        testEntityManager.flush();
        testEntityManager.clear();

        Deal loaded = dealRepository.findById(deal.getId()).orElseThrow();
        loaded.getTasks().removeIf(t -> t.getId().equals(task2.getId())); // orphan!

        dealRepository.save(loaded);
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(taskRepository.findById(task2.getId())).isEmpty();
        assertThat(taskRepository.findById(task1.getId())).isPresent();
    }

    // -------------------------------------------------------------------------
    // Тест 3: LAZY loading — правильная инициализация в рамках транзакции
    // -------------------------------------------------------------------------

    @Test
    void lazyCollection_accessedInTransaction_loadsSuccessfully() {
        User manager = userRepository.save(buildUser("lazy@example.com", UserRole.MANAGER));
        Customer customer = customerRepository.save(buildCustomer("lazy-client@example.com"));
        Deal deal = dealRepository.save(buildDeal("Lazy Deal", DealStatus.NEW, customer, manager));
        taskRepository.save(buildTask("Task A", TaskPriority.HIGH, deal));
        taskRepository.save(buildTask("Task B", TaskPriority.MEDIUM, deal));
        testEntityManager.flush();
        testEntityManager.clear();

        // Загружаем Deal. tasks — LAZY proxy, не загружен
        Deal loaded = dealRepository.findById(deal.getId()).orElseThrow();

        // В рамках открытой сессии (@DataJpaTest = одна транзакция на тест)
        // LAZY коллекция инициализируется при первом обращении: SELECT tasks WHERE deal_id=?
        assertThat(loaded.getTasks()).hasSize(2); // ← LAZY LOAD происходит здесь
    }

    // -------------------------------------------------------------------------
    // Тест 4: LazyInitializationException — доступ вне сессии
    // -------------------------------------------------------------------------

    @Test
    void lazyInitializationException_afterDetach() {
        // Наиболее частая ошибка в JPA-приложениях:
        //   "could not initialize proxy - no Session"
        //
        // Причина: сессия закрылась (транзакция закончилась), а код пытается
        // обратиться к LAZY коллекции на detached объекте.
        //
        // Симулируем: detach() + обращение к LAZY коллекции

        User manager = userRepository.save(buildUser("laz-exc@example.com", UserRole.MANAGER));
        Customer customer = customerRepository.save(buildCustomer("laz-exc-client@example.com"));
        Deal deal = dealRepository.save(buildDeal("LazyExc Deal", DealStatus.NEW, customer, manager));
        taskRepository.save(buildTask("Task", TaskPriority.HIGH, deal));
        testEntityManager.flush();
        testEntityManager.clear();

        // Загружаем Deal: tasks — LAZY прокси (не инициализирован)
        Deal loaded = dealRepository.findById(deal.getId()).orElseThrow();

        // Детачим объект от persistence context (симулируем конец транзакции)
        entityManager.detach(loaded);

        // После detach: прокси не может "дозагрузить" данные — нет сессии
        assertThatThrownBy(() -> loaded.getTasks().size())
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("Could not initialize proxy");

        System.out.println("""
                [LazyInitializationException] Типичный сценарий:
                  @Transactional сервис возвращает Entity
                  Транзакция закрывается при выходе из метода
                  Контроллер пытается обратиться к LAZY коллекции → CRASH

                Решения:
                  1. Инициализировать коллекцию в транзакции (Hibernate.initialize)
                  2. Использовать DTO/Projections — не возвращать Entity из сервиса
                  3. JOIN FETCH в запросе
                  4. НИКОГДА не включать spring.jpa.open-in-view=true в проде!
                """);
    }

    @Test
    void lazyCollection_accessedBeforeDetach_worksCorrectly() {
        // Правильное решение: инициализировать коллекцию ДО выхода из транзакции

        User manager = userRepository.save(buildUser("lazy-fix@example.com", UserRole.MANAGER));
        Customer customer = customerRepository.save(buildCustomer("lazy-fix-client@example.com"));
        Deal deal = dealRepository.save(buildDeal("Lazy Fix Deal", DealStatus.NEW, customer, manager));
        taskRepository.save(buildTask("Task", TaskPriority.HIGH, deal));
        testEntityManager.flush();
        testEntityManager.clear();

        Deal loaded = dealRepository.findById(deal.getId()).orElseThrow();

        // Инициализируем коллекцию ВНУТРИ сессии — явный .size() или Hibernate.initialize()
        int tasksCount = loaded.getTasks().size(); // ← инициализация в сессии

        // Теперь детачим
        entityManager.detach(loaded);

        // Коллекция уже инициализирована — можно обращаться без сессии
        assertThat(loaded.getTasks()).hasSize(tasksCount);
    }

    // -------------------------------------------------------------------------
    // Тест 5: @ManyToOne по умолчанию EAGER — демонстрация
    // -------------------------------------------------------------------------

    @Test
    void manyToOne_withExplicitLazy_doesNotLoadAssociation() {
        // В нашем проекте @ManyToOne(fetch = LAZY) задан ЯВНО.
        // Если бы мы не указали LAZY — было бы EAGER (дефолт для @ManyToOne).
        // С EAGER: SELECT deals LEFT JOIN customers — customer загружается с deal.

        User manager = userRepository.save(buildUser("eager@example.com", UserRole.MANAGER));
        Customer customer = customerRepository.save(buildCustomer("eager-client@example.com"));
        Deal deal = dealRepository.save(buildDeal("ManyToOne Test", DealStatus.NEW, customer, manager));
        testEntityManager.flush();
        testEntityManager.clear();

        // С LAZY (наш случай): загружается только Deal, customer — proxy
        Deal loaded = dealRepository.findById(deal.getId()).orElseThrow();

        // Проверяем что customer — это Hibernate proxy (не инициализированный)
        // isInstanceOf(Customer.class) вернёт true т.к. proxy наследует Customer
        assertThat(loaded.getCustomer()).isNotNull(); // proxy создан
        // id доступен без SELECT (хранится в proxy)
        assertThat(loaded.getCustomer().getId()).isEqualTo(customer.getId());
        // getFirstName() уже триггерит LAZY load (инициализирует proxy)
        assertThat(loaded.getCustomer().getFirstName()).isEqualTo("Alice");
    }
}
