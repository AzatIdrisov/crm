package com.crm.repository;

import com.crm.config.JpaConfig;
import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.Task;
import com.crm.model.User;
import com.crm.model.enums.DealStatus;
import com.crm.model.enums.TaskPriority;
import com.crm.model.enums.UserRole;
import com.crm.model.value.Email;
import com.crm.model.value.Money;
import com.crm.model.value.PhoneNumber;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

/**
 * Базовый класс для всех JPA-тестов.
 *
 * Ключевые концепции:
 *  - @DataJpaTest: поднимает только JPA-слой (без @Service, @Controller).
 *    Загружает: @Entity, @Embeddable, @Converter, @Repository, JPA конфигурацию.
 *    Каждый тест по умолчанию обёрнут в транзакцию, которая откатывается после теста.
 *
 *  - @AutoConfigureTestDatabase(replace = NONE): запрещает замену нашего DataSource
 *    на встроенную H2. Без этого Spring Boot заменил бы PostgreSQL на H2, и наш
 *    PostgreSQL-специфичный SQL в Liquibase не запустился бы.
 *
 *  - @Import(JpaConfig.class): вручную подключаем @EnableJpaAuditing.
 *    @DataJpaTest не подгружает @Configuration классы автоматически.
 *    Без этого @CreatedDate/@LastModifiedDate не заполнялись бы в тестах.
 *
 *  - @Testcontainers: JUnit 5 расширение, управляет жизненным циклом контейнеров.
 *    static контейнер: запускается один раз на все тесты в JVM (переиспользуется
 *    между тестовыми классами благодаря Spring context reuse).
 *
 *  - @DynamicPropertySource: переопределяет spring.datasource.* свойства,
 *    направляя приложение к контейнерному PostgreSQL.
 *    Вызывается до создания ApplicationContext.
 *
 *  - Liquibase: запускается автоматически при старте контекста.
 *    Создаёт все таблицы из наших changesets (001-init-schema.sql, 002-indexes.sql).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
// disabledWithoutDocker = true: тесты ПРОПУСКАЮТСЯ (Skipped) если Docker недоступен,
// вместо того чтобы падать с ошибкой. Это важно для CI/CD пайплайнов, где Docker
// может отсутствовать, и для локальной разработки без запущенного Docker.
// Чтобы запустить тесты: запустить Docker Desktop или Docker Engine.
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crm_test")
            .withUsername("crm")
            .withPassword("crm");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // -------------------------------------------------------------------------
    // Фабричные методы для построения тестовых объектов
    // -------------------------------------------------------------------------

    protected User buildUser(String email, UserRole role) {
        return User.builder()
                .email(new Email(email))
                .password("hashed-password")
                .firstName("Ivan")
                .lastName("Petrov")
                .role(role)
                .build();
    }

    protected Customer buildCustomer(String email) {
        return Customer.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email(new Email(email))
                .phone(new PhoneNumber("+7 999 123-45-67"))
                .company("Acme Corp")
                .build();
    }

    protected Deal buildDeal(String title, DealStatus status, Customer customer, User assignedTo) {
        return Deal.builder()
                .title(title)
                .status(status)
                .amount(Money.ofRub(new BigDecimal("100000")))
                .customer(customer)
                .assignedTo(assignedTo)
                .build();
    }

    protected Task buildTask(String title, TaskPriority priority, Deal deal) {
        return Task.builder()
                .title(title)
                .priority(priority)
                .deal(deal)
                .completed(false)
                .build();
    }
}
