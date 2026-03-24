package com.crm.service;

import com.crm.model.Customer;
import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import com.crm.model.value.Email;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
public class CustomerService {

    // In-memory хранилище — заменится на репозиторий в Фазе 5
    private final Map<Long, Customer> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    // -------------------------------------------------------------------------
    // Optional вместо null-проверок
    // -------------------------------------------------------------------------

    public Optional<Customer> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public Optional<Customer> findByEmail(Email email) {
        return store
                .values()
                .stream()
                .filter(customer -> email.equals(customer.getEmail()))
                .findFirst();
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

    public Customer save(Customer customer) {
        if (customer.getId() == null) {
            customer.setId(idSequence.getAndIncrement());
        }
        store.put(customer.getId(), customer);
        return customer;
    }

    public List<Customer> findAll() {
        return new ArrayList<>(store.values());
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
        return store.values().stream().filter(predicate).toList();
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
