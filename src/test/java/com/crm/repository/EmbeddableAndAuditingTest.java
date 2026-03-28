package com.crm.repository;

import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.User;
import com.crm.model.enums.DealStatus;
import com.crm.model.enums.UserRole;
import com.crm.model.value.Email;
import com.crm.model.value.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты для @Embeddable Value Objects, JPA Auditing и Derived Queries.
 *
 * Что демонстрируют эти тесты:
 *  1. @Embeddable record (Email, PhoneNumber, Money) сохраняется как колонки родительской таблицы
 *  2. @AttributeOverride переименовывает колонки (value → email, value → phone)
 *  3. Money.amount и Money.currency — две отдельные колонки в таблице deals
 *  4. CurrencyConverter(autoApply) — Currency конвертируется в "RUB"/"USD" автоматически
 *  5. @CreatedDate/@LastModifiedDate — заполняются автоматически Spring Data JPA Auditing
 *  6. @Enumerated(STRING) — DealStatus хранится как "NEW", "WON", не как 0, 1
 *  7. Derived queries через embedded поле: findByEmailValue(String)
 */
class EmbeddableAndAuditingTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private UserRepository userRepository;

    // -------------------------------------------------------------------------
    // @Embeddable: Email и PhoneNumber сохраняются как одна колонка
    // -------------------------------------------------------------------------

    @Test
    void email_storedAsStringColumn_restoredAsRecord() {
        // КОГДА: сохраняем Customer с Email value object
        Customer customer = buildCustomer("test@example.com");
        Customer saved = customerRepository.save(customer);

        // Очищаем L1 кэш, чтобы следующий запрос шёл в БД
        entityManager.flush();
        entityManager.clear();

        // ТОГДА: Email восстанавливается из БД как record
        Customer loaded = customerRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getEmail()).isNotNull();
        assertThat(loaded.getEmail().value()).isEqualTo("test@example.com");
        assertThat(loaded.getEmail()).isInstanceOf(Email.class);

        // Проверяем, что @AttributeOverride сработал:
        // колонка называется "email", а не "value" (имя компонента record'а)
        Object rawEmail = entityManager.getEntityManager()
                .createNativeQuery("SELECT email FROM customers WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();
        assertThat(rawEmail).isEqualTo("test@example.com");
    }

    @Test
    void phoneNumber_storedAsStringColumn() {
        Customer customer = buildCustomer("phone@example.com");
        Customer saved = customerRepository.save(customer);
        entityManager.flush();
        entityManager.clear();

        // PhoneNumber хранится в колонке "phone", не "value"
        Object rawPhone = entityManager.getEntityManager()
                .createNativeQuery("SELECT phone FROM customers WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();
        assertThat(rawPhone).isEqualTo("+7 999 123-45-67");
    }

    // -------------------------------------------------------------------------
    // @Embeddable: Money сохраняется как TWO колонки (amount + currency)
    // -------------------------------------------------------------------------

    @Test
    void money_storedAsTwoColumns_restoredWithConverter() {
        // ДАНО: Deal со значением Money(50000 RUB)
        User user = userRepository.save(buildUser("manager@example.com", UserRole.MANAGER));
        Customer customer = customerRepository.save(buildCustomer("client@example.com"));
        Deal deal = Deal.builder()
                .title("Big Deal")
                .status(DealStatus.NEW)
                .amount(Money.of(new BigDecimal("50000"), "RUB"))
                .customer(customer)
                .assignedTo(user)
                .build();
        Deal saved = dealRepository.save(deal);
        entityManager.flush();
        entityManager.clear();

        // ТОГДА: amount и currency — отдельные колонки в таблице deals
        Object[] raw = (Object[]) entityManager.getEntityManager()
                .createNativeQuery("SELECT amount, currency FROM deals WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();

        assertThat(raw[0]).isEqualTo(new BigDecimal("50000.00")); // DECIMAL(19,2)
        assertThat(raw[1]).isEqualTo("RUB");                      // CurrencyConverter: Currency → "RUB"

        // И Money восстанавливается как record через CurrencyConverter
        Deal loaded = dealRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getAmount().amount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(loaded.getAmount().currency()).isEqualTo(Currency.getInstance("RUB"));
    }

    @Test
    void money_nullAmount_storedAsNullColumns() {
        // Deal без суммы: обе колонки NULL
        User user = userRepository.save(buildUser("user2@example.com", UserRole.MANAGER));
        Deal deal = Deal.builder()
                .title("Deal without amount")
                .status(DealStatus.NEW)
                .amount(null)
                .assignedTo(user)
                .build();
        Deal saved = dealRepository.save(deal);
        entityManager.flush();
        entityManager.clear();

        Object[] raw = (Object[]) entityManager.getEntityManager()
                .createNativeQuery("SELECT amount, currency FROM deals WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();

        assertThat(raw[0]).isNull();
        assertThat(raw[1]).isNull();
    }

    // -------------------------------------------------------------------------
    // @Enumerated(STRING): DealStatus хранится как строка
    // -------------------------------------------------------------------------

    @Test
    void dealStatus_storedAsString_notOrdinal() {
        // Ключевой нюанс: @Enumerated(ORDINAL) — дефолт — хранил бы 0, 1, 2...
        // Если добавить новый статус в середину enum — все данные сломались бы.
        // @Enumerated(STRING) хранит "NEW", "WON" — безопасно для изменений enum.
        User user = userRepository.save(buildUser("enum@example.com", UserRole.MANAGER));
        Deal deal = buildDeal("Status Test", DealStatus.WON, null, user);
        Deal saved = dealRepository.save(deal);
        entityManager.flush();

        Object rawStatus = entityManager.getEntityManager()
                .createNativeQuery("SELECT status FROM deals WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();

        // В БД строка "WON", а не 2 (порядковый номер в enum)
        assertThat(rawStatus).isEqualTo("WON");
    }

    // -------------------------------------------------------------------------
    // JPA Auditing: @CreatedDate и @LastModifiedDate
    // -------------------------------------------------------------------------

    @Test
    void createdAt_isAutoPopulated_onSave() {
        // @EnableJpaAuditing + @EntityListeners(AuditingEntityListener) в BaseEntity
        // автоматически проставляют createdAt при persist
        Customer customer = buildCustomer("audit@example.com");
        assertThat(customer.getCreatedAt()).isNull(); // до сохранения — null

        Customer saved = customerRepository.save(customer);
        entityManager.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void updatedAt_isRefreshed_onUpdate_but_createdAt_isNot() {
        // @Column(updatable = false) на createdAt: значение устанавливается один раз
        Customer customer = customerRepository.save(buildCustomer("update@example.com"));
        entityManager.flush();
        entityManager.clear();

        Customer loaded = customerRepository.findById(customer.getId()).orElseThrow();
        var createdAtBefore = loaded.getCreatedAt();
        var updatedAtBefore = loaded.getUpdatedAt();

        // Ждём немного, чтобы updatedAt изменился (точность — миллисекунды)
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        loaded.setCompany("Updated Company");
        customerRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        Customer updated = customerRepository.findById(customer.getId()).orElseThrow();

        // createdAt не должен измениться (@Column(updatable = false))
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        // updatedAt обновился
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updatedAtBefore);
    }

    // -------------------------------------------------------------------------
    // Derived queries для @Embedded поля
    // -------------------------------------------------------------------------

    @Test
    void findByEmailValue_accessesEmbeddedComponent() {
        // Spring Data генерирует: WHERE u.email.value = :emailValue
        // "email" — имя @Embedded поля, "value" — компонент record Email
        Customer customer = customerRepository.save(buildCustomer("derived@example.com"));
        entityManager.flush();
        entityManager.clear();

        Optional<Customer> found = customerRepository.findByEmailValue("derived@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(customer.getId());
    }

    @Test
    void existsByEmailValue_efficientCheck_noObjectLoad() {
        // existsBy генерирует SELECT COUNT(*) > 0 — не загружает объект
        customerRepository.save(buildCustomer("exists@example.com"));
        entityManager.flush();

        assertThat(customerRepository.existsByEmailValue("exists@example.com")).isTrue();
        assertThat(customerRepository.existsByEmailValue("missing@example.com")).isFalse();
    }
}
