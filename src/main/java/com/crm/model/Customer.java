package com.crm.model;

import com.crm.model.value.Email;
import com.crm.model.value.PhoneNumber;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-сущность клиента.
 *
 * Ключевые концепции:
 *  - @OneToMany(mappedBy = "customer"): обратная сторона двунаправленной связи.
 *    "mappedBy" говорит JPA: «владелец связи — поле customer в Deal».
 *    Это означает, что в таблице deals есть FK-колонка customer_id, а в таблице
 *    customers НЕТ никакой дополнительной колонки для хранения связи.
 *
 *  - cascade = CascadeType.ALL: операции persist/merge/remove на Customer
 *    автоматически каскадируются на все его Deal.
 *    Осторожно: CascadeType.REMOVE удалит все сделки при удалении клиента.
 *    Альтернатива — orphanRemoval = true (удаляет только "осиротевшие").
 *
 *  - fetch = FetchType.LAZY (дефолт для @OneToMany): коллекция deals НЕ
 *    загружается при загрузке Customer. Она загрузится только при первом
 *    обращении customer.getDeals(). Это предотвращает N+1 проблему.
 *
 *  - Инициализация deals = new ArrayList<>(): без этого customer.getDeals()
 *    вернёт null для новых несохранённых объектов — добавление в коллекцию
 *    упадёт с NPE.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Customer extends BaseEntity<Long> {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email", unique = true, length = 255))
    private Email email;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "phone", length = 50))
    private PhoneNumber phone;

    @Column(length = 255)
    private String company;

    // Двунаправленная связь: одному клиенту соответствует много сделок.
    // LAZY — не загружаем все сделки при каждом запросе клиента.
    // cascade ALL + orphanRemoval — если убрать сделку из списка, она удалится из БД.
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Deal> deals = new ArrayList<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
