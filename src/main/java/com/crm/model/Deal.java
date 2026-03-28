package com.crm.model;

import com.crm.model.enums.DealStatus;
import com.crm.model.value.Money;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * JPA-сущность сделки.
 *
 * Ключевые концепции:
 *  - @ManyToOne(fetch = LAZY): ВАЖНО — дефолт для @ManyToOne это EAGER!
 *    Это значит: при загрузке Deal Hibernate по умолчанию делает JOIN и
 *    тут же загружает Customer и User. Если загружаем список из 100 сделок —
 *    Hibernate делает SELECT deals + отдельные SELECT для каждого customer
 *    и assignedTo. Это и есть N+1 проблема.
 *    Решение: явно указывать fetch = LAZY и использовать JOIN FETCH в JPQL
 *    там, где связанные объекты нужны.
 *
 *  - @JoinColumn(name = "customer_id"): имя FK-колонки в таблице deals.
 *    Без этой аннотации Hibernate придумает имя сам (обычно customer_id,
 *    но лучше явно).
 *
 *  - @Version: оптимистичная блокировка (optimistic locking).
 *    При UPDATE Hibernate добавляет в WHERE: AND version = <старое_значение>.
 *    Если строку успел изменить другой поток — version уже другая, UPDATE
 *    вернёт 0 строк → Hibernate бросает OptimisticLockException.
 *    Это предотвращает потерю обновлений (lost update) без пессимистичной блокировки.
 *
 *  - @Embedded + @AttributeOverrides: Money имеет два компонента — amount и currency.
 *    Они маппятся в две колонки: "amount" и "currency".
 *    Currency конвертируется через CurrencyConverter (autoApply = true).
 *
 *  - @OneToMany(mappedBy): двунаправленная связь Deal → Task и Deal → Comment.
 *    Task и Comment являются "владельцами" связи (у них FK).
 */
@Entity
@Table(name = "deals")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Deal extends BaseEntity<Long> implements Comparable<Deal> {

    @Column(nullable = false, length = 500)
    private String title;

    // @Embeddable Money: два поля в одной таблице. Если amount == null,
    // Hibernate сохраняет NULL в обе колонки (amount и currency).
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount",   column = @Column(name = "amount", precision = 19, scale = 2)),
        @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
    })
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DealStatus status;

    // fetch = LAZY — НЕ загружать Customer при загрузке Deal без явного запроса.
    // Без этого каждый SELECT deals вызовет SELECT customer для каждой строки (N+1).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // Оптимистичная блокировка: Hibernate автоматически инкрементит при UPDATE.
    // Тип Long (не long): null означает "новый объект, ещё не сохранён".
    @Version
    private Long version;

    @OneToMany(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Task> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Comment> comments = new ArrayList<>();

    // Естественный порядок: по сумме сделки по убыванию (крупные сделки — первыми)
    @Override
    public int compareTo(Deal other) {
        return other.amount.amount().compareTo(this.amount.amount());
    }

    // Сортировка по статусу (порядок объявления в enum)
    public static final Comparator<Deal> BY_STATUS =
            Comparator.comparing(Deal::getStatus);

    // Сортировка по дате закрытия: ближайшие дедлайны — первыми, null — в конец
    public static final Comparator<Deal> BY_CLOSE_DATE =
            Comparator.comparing(Deal::getClosedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));

    // Комбинированная сортировка: сначала по статусу, затем по сумме по убыванию
    public static final Comparator<Deal> BY_STATUS_THEN_AMOUNT =
            Comparator.comparing(Deal::getStatus)
                      .thenComparing(Comparator.comparing(d -> d.getAmount().amount(),
                              Comparator.reverseOrder()));
}
