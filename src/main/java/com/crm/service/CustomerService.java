package com.crm.service;

import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import com.crm.model.value.Email;
import com.crm.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Сервис клиентов — Phase 5: заменяем in-memory ConcurrentHashMap на CustomerRepository.
 *
 * Ключевые концепции:
 *  - @Transactional на классе: все public-методы выполняются в транзакции.
 *    readOnly = true снижает нагрузку: Hibernate пропускает dirty-checking,
 *    PostgreSQL может использовать read-only replica.
 *
 *  - @Transactional на методе переопределяет аннотацию класса.
 *    Методы записи (save, delete) должны явно объявить readOnly = false (дефолт).
 *
 *  - Функциональные интерфейсы (Predicate, Function, BiFunction) остаются —
 *    они работают с доменными объектами в памяти после загрузки из БД.
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    // -------------------------------------------------------------------------
    // Optional вместо null-проверок
    // -------------------------------------------------------------------------

    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id);
    }

    public Optional<Customer> findByEmail(Email email) {
        return customerRepository.findByEmailValue(email.value());
    }

    public Customer getByIdOrThrow(Long id) {
        return findById(id).orElseThrow(NoSuchElementException::new);
    }

    public String getDisplayName(Long id) {
        return findById(id)
                .map(Customer::getFullName)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .orElse("Unknown Customer");
    }

    public void printIfExists(Long id) {
        findById(id).ifPresent(c -> System.out.println(c.getFullName()));
    }

    @Transactional
    public Customer save(Customer customer) {
        return customerRepository.save(customer);
    }

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    @Transactional
    public boolean deleteById(Long id) {
        if (!customerRepository.existsById(id)) {
            return false;
        }
        customerRepository.deleteById(id);
        return true;
    }

    // -------------------------------------------------------------------------
    // Predicate<Customer>
    // -------------------------------------------------------------------------

    public static final Predicate<Customer> HAS_COMPANY = customer -> (customer.getCompany() != null) && (!customer.getCompany().isBlank());

    public static final Predicate<Customer> HAS_PHONE = customer -> (customer.getPhone() != null);

    public static final Predicate<Customer> HAS_EMAIL = customer -> customer.getEmail() != null;

    public static Predicate<Customer> fromCompany(String company) {
        return customer -> company.equalsIgnoreCase(customer.getCompany());
    }

    public static Predicate<Customer> isFullyContacted() {
        return HAS_EMAIL.and(HAS_PHONE);
    }

    public List<Customer> filter(Predicate<Customer> predicate) {
        return customerRepository.findAll().stream().filter(predicate).toList();
    }

    // -------------------------------------------------------------------------
    // Function<Customer, R>
    // -------------------------------------------------------------------------

    public static final Function<Customer, String> TO_DISPLAY_NAME =
            c -> "%s %s (%s)".formatted(
                    c.getFirstName(),
                    c.getLastName(),
                    Optional.ofNullable(c.getCompany()).orElse("no company")
            );

    public static final Function<Customer, String> TO_UPPER_DISPLAY = TO_DISPLAY_NAME.andThen(String::toUpperCase);

    public List<String> mapToDisplayNames(List<Customer> customers) {
        return customers.stream().map(TO_DISPLAY_NAME).toList();
    }

    // -------------------------------------------------------------------------
    // BiFunction
    // -------------------------------------------------------------------------

    public static final BiFunction<Customer, List<Deal>, List<Deal>> GET_CUSTOMER_DEALS =
            (customer, deals) -> deals.stream()
                    .filter(d -> customer.equals(d.getCustomer()))
                    .toList();

    public static BiFunction<Customer, DealStatus, Predicate<Deal>> dealsByStatus() {
        return (customer, status) ->
                d -> customer.equals(d.getCustomer()) && d.getStatus() == status;
    }
}
