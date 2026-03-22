package com.crm.model;

import com.crm.model.enums.DealStatus;
import com.crm.model.value.Money;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Comparator;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deal extends BaseEntity<Long> implements Comparable<Deal> {

    private String title;
    private Money amount;
    private DealStatus status;
    private Customer customer;
    private User assignedTo;
    private LocalDateTime closedAt;

    // Естественный порядок: по сумме сделки по убыванию (крупные сделки — первыми)
    @Override
    public int compareTo(Deal other) {
        return other.amount.amount().compareTo(this.amount.amount());
    }

    // Сортировка по статусу (порядок объявления в enum: NEW → IN_PROGRESS → WON → LOST → ON_HOLD)
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
