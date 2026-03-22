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

    // TODO 2.2.1: вернуть Optional<Customer> по id (использовать Optional.ofNullable)
    public Optional<Customer> findById(Long id) {
        return Optional.empty();
    }

    // TODO 2.2.2: найти клиента по email через stream + findFirst, вернуть Optional
    public Optional<Customer> findByEmail(Email email) {
        return Optional.empty();
    }

    // TODO 2.2.3: получить клиента или бросить NoSuchElementException через orElseThrow
    public Customer getByIdOrThrow(Long id) {
        return null;
    }

    // TODO 2.2.4: вернуть отображаемое имя через цепочку map → map → filter → orElse
    //             если имя пустое или клиент не найден — вернуть "Unknown Customer"
    public String getDisplayName(Long id) {
        return null;
    }

    // TODO 2.2.5: вывести в консоль имя клиента через ifPresent (если найден)
    public void printIfExists(Long id) {
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

    // TODO 2.3.1: предикат — у клиента заполнена компания (не null и не blank)
    public static final Predicate<Customer> HAS_COMPANY = null;

    // TODO 2.3.2: предикат — у клиента есть телефон
    public static final Predicate<Customer> HAS_PHONE = null;

    // TODO 2.3.3: предикат — у клиента есть email
    public static final Predicate<Customer> HAS_EMAIL = null;

    // TODO 2.3.4: фабричный метод — предикат "клиент из заданной компании" (ignoreCase)
    public static Predicate<Customer> fromCompany(String company) {
        return null;
    }

    // TODO 2.3.5: скомбинировать HAS_EMAIL и HAS_PHONE через and()
    public static Predicate<Customer> isFullyContacted() {
        return null;
    }

    // TODO 2.3.6: отфильтровать store по произвольному Predicate и вернуть список
    public List<Customer> filter(Predicate<Customer> predicate) {
        return null;
    }

    // -------------------------------------------------------------------------
    // Function<Customer, R>
    // -------------------------------------------------------------------------

    // TODO 2.3.7: Function — преобразовать клиента в строку "Имя Фамилия (компания)"
    //             если компания null — подставить "no company"
    public static final Function<Customer, String> TO_DISPLAY_NAME = null;

    // TODO 2.3.8: Function composition через andThen — TO_DISPLAY_NAME + toUpperCase
    public static final Function<Customer, String> TO_UPPER_DISPLAY = null;

    // TODO 2.3.9: применить TO_DISPLAY_NAME ко всем клиентам через stream + map
    public List<String> mapToDisplayNames(List<Customer> customers) {
        return null;
    }

    // -------------------------------------------------------------------------
    // BiFunction
    // -------------------------------------------------------------------------

    // TODO 2.3.10: BiFunction(клиент, список сделок) → сделки этого клиента
    public static final BiFunction<Customer, List<Deal>, List<Deal>> GET_CUSTOMER_DEALS = null;

    // TODO 2.3.11: BiFunction(клиент, статус) → Predicate<Deal>
    //              результирующий предикат: сделка принадлежит клиенту И имеет нужный статус
    public static BiFunction<Customer, DealStatus, Predicate<Deal>> dealsByStatus() {
        return null;
    }
}
