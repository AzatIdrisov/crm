package com.crm.service;

import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import com.crm.model.enums.UserRole;
import com.crm.model.value.Email;
import com.crm.model.value.Money;
import com.crm.model.value.PhoneNumber;
import com.crm.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты CustomerService.
 *
 * Ключевые концепции:
 *  - @ExtendWith(MockitoExtension.class): JUnit 5 расширение, которое обрабатывает
 *    @Mock и @InjectMocks без Spring контекста. Тесты запускаются в ~10x быстрее
 *    чем с полным Spring контекстом.
 *
 *  - @Mock: создаёт Mockito-прокси для CustomerRepository. Все методы по умолчанию
 *    возвращают "пустые" значения: null, 0, false, Optional.empty(), пустые коллекции.
 *
 *  - @InjectMocks: создаёт реальный CustomerService и инжектирует @Mock поля
 *    через конструктор (приоритет) или поля. @Transactional, @Cacheable и другие
 *    Spring-аннотации на сервисе НЕ активны — мы тестируем только логику метода.
 *
 *  - when(...).thenReturn(...): задаём поведение mock ПЕРЕД вызовом тестируемого метода.
 *  - verify(...): проверяем, что метод mock был вызван с нужными аргументами.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService Unit Tests")
class CustomerServiceTest {

    @Mock
    CustomerRepository customerRepository;

    @InjectMocks
    CustomerService customerService;

    // Тестовые данные — создаём один раз в @BeforeEach
    private Customer alice;
    private Customer bob;

    @BeforeEach
    void setUp() {
        alice = Customer.builder()
                .id(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email(new Email("alice@test.com"))
                .phone(new PhoneNumber("+7 999 000-00-00"))
                .company("Acme Corp")
                .build();

        // Клиент без компании и телефона — для тестирования предикатов
        bob = Customer.builder()
                .id(2L)
                .firstName("Bob")
                .lastName("Jones")
                .email(new Email("bob@test.com"))
                .phone(null)
                .company(null)
                .build();
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Test
    @DisplayName("findById: возвращает Optional.of(customer) если найден")
    void findById_whenFound_returnsOptionalCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(alice));

        Optional<Customer> result = customerService.findById(1L);

        assertThat(result).isPresent().contains(alice);
        // Проверяем что репозиторий был вызван ровно один раз
        verify(customerRepository).findById(1L);
    }

    @Test
    @DisplayName("findById: возвращает Optional.empty() если не найден")
    void findById_whenNotFound_returnsEmpty() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Customer> result = customerService.findById(99L);

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // getByIdOrThrow
    // =========================================================================

    @Test
    @DisplayName("getByIdOrThrow: возвращает клиента если найден")
    void getByIdOrThrow_whenFound_returnsCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(alice));

        Customer result = customerService.getByIdOrThrow(1L);

        assertThat(result).isEqualTo(alice);
    }

    @Test
    @DisplayName("getByIdOrThrow: бросает NoSuchElementException если не найден")
    void getByIdOrThrow_whenNotFound_throwsException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        // assertThatThrownBy: проверяем тип исключения без try/catch
        assertThatThrownBy(() -> customerService.getByIdOrThrow(99L))
                .isInstanceOf(NoSuchElementException.class);
    }

    // =========================================================================
    // getDisplayName
    // =========================================================================

    @Test
    @DisplayName("getDisplayName: возвращает полное имя клиента")
    void getDisplayName_whenCustomerExists_returnsFullName() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(alice));

        String name = customerService.getDisplayName(1L);

        assertThat(name).isEqualTo("Alice Smith");
    }

    @Test
    @DisplayName("getDisplayName: возвращает 'Unknown Customer' если клиент не найден")
    void getDisplayName_whenNotFound_returnsUnknown() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        String name = customerService.getDisplayName(99L);

        assertThat(name).isEqualTo("Unknown Customer");
    }

    // =========================================================================
    // save
    // =========================================================================

    @Test
    @DisplayName("save: делегирует в репозиторий и возвращает результат")
    void save_delegatesToRepository() {
        // any() — любой аргумент типа Customer
        when(customerRepository.save(any(Customer.class))).thenReturn(alice);

        Customer result = customerService.save(alice);

        assertThat(result).isEqualTo(alice);
        verify(customerRepository).save(alice);
    }

    // =========================================================================
    // deleteById
    // =========================================================================

    @Test
    @DisplayName("deleteById: возвращает false если клиент не существует")
    void deleteById_whenNotExists_returnsFalse() {
        when(customerRepository.existsById(99L)).thenReturn(false);

        boolean result = customerService.deleteById(99L);

        assertThat(result).isFalse();
        // delete НЕ должен вызываться если клиент не найден
        verify(customerRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteById: удаляет клиента и возвращает true")
    void deleteById_whenExists_deletesAndReturnsTrue() {
        when(customerRepository.existsById(1L)).thenReturn(true);

        boolean result = customerService.deleteById(1L);

        assertThat(result).isTrue();
        verify(customerRepository).deleteById(1L);
    }

    // =========================================================================
    // Predicate<Customer> — тестируем через метод filter()
    // =========================================================================

    @Test
    @DisplayName("HAS_COMPANY: фильтрует только клиентов с компанией")
    void filter_hasCompany_returnsOnlyCustomersWithCompany() {
        when(customerRepository.findAll()).thenReturn(List.of(alice, bob));

        List<Customer> result = customerService.filter(CustomerService.HAS_COMPANY);

        // alice имеет компанию, bob — нет
        assertThat(result).containsExactly(alice);
    }

    @Test
    @DisplayName("isFullyContacted: возвращает клиентов с email И телефоном")
    void filter_isFullyContacted_requiresEmailAndPhone() {
        when(customerRepository.findAll()).thenReturn(List.of(alice, bob));

        // alice: email + phone, bob: email, нет phone
        List<Customer> result = customerService.filter(CustomerService.isFullyContacted());

        assertThat(result).containsExactly(alice);
        assertThat(result).doesNotContain(bob);
    }

    @Test
    @DisplayName("fromCompany: фильтрует по названию компании без учёта регистра")
    void filter_fromCompany_caseInsensitive() {
        when(customerRepository.findAll()).thenReturn(List.of(alice, bob));

        Predicate<Customer> pred = CustomerService.fromCompany("acme corp");
        List<Customer> result = customerService.filter(pred);

        assertThat(result).containsExactly(alice);
    }

    // =========================================================================
    // Function<Customer, String> — TO_DISPLAY_NAME
    // =========================================================================

    @Test
    @DisplayName("mapToDisplayNames: форматирует имя + фамилия + (компания)")
    void mapToDisplayNames_formatsCorrectly() {
        List<String> names = customerService.mapToDisplayNames(List.of(alice, bob));

        assertThat(names).containsExactly(
                "Alice Smith (Acme Corp)",
                "Bob Jones (no company)"   // company=null → "no company"
        );
    }

    // =========================================================================
    // BiFunction — GET_CUSTOMER_DEALS
    // =========================================================================

    @Test
    @DisplayName("GET_CUSTOMER_DEALS: возвращает только сделки нужного клиента")
    void getCustomerDeals_returnsOnlyMatchingDeals() {
        Deal aliceDeal = Deal.builder()
                .id(1L)
                .title("Alice Deal")
                .status(DealStatus.NEW)
                .amount(Money.ofRub(new BigDecimal("5000")))
                .customer(alice)
                .build();

        Deal bobDeal = Deal.builder()
                .id(2L)
                .title("Bob Deal")
                .status(DealStatus.NEW)
                .amount(Money.ofRub(new BigDecimal("3000")))
                .customer(bob)
                .build();

        List<Deal> allDeals = List.of(aliceDeal, bobDeal);

        List<Deal> aliceDeals = CustomerService.GET_CUSTOMER_DEALS.apply(alice, allDeals);

        assertThat(aliceDeals).containsExactly(aliceDeal);
        assertThat(aliceDeals).doesNotContain(bobDeal);
    }

    @Test
    @DisplayName("dealsByStatus: фильтрует по клиенту И статусу")
    void dealsByStatus_filtersCorrectly() {
        Deal wonDeal = Deal.builder()
                .id(1L).title("Won").status(DealStatus.WON)
                .amount(Money.ofRub(new BigDecimal("1000"))).customer(alice).build();

        Deal newDeal = Deal.builder()
                .id(2L).title("New").status(DealStatus.NEW)
                .amount(Money.ofRub(new BigDecimal("2000"))).customer(alice).build();

        // dealsByStatus() возвращает BiFunction<Customer, DealStatus, Predicate<Deal>>
        Predicate<Deal> predicate = CustomerService.dealsByStatus().apply(alice, DealStatus.WON);

        assertThat(predicate.test(wonDeal)).isTrue();
        assertThat(predicate.test(newDeal)).isFalse();  // статус не совпадает
    }
}
