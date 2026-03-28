package com.crm.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * JPA-сущность комментария к сделке.
 *
 * Нюанс: @Column(columnDefinition = "TEXT") — для длинных строк без ограничения длины.
 * VARCHAR(255) не хватит для произвольных комментариев.
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Comment extends BaseEntity<Long> {

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    // ON DELETE CASCADE в DDL (Liquibase) + orphanRemoval в Comment → Deal.
    // Если сделка удаляется — все комментарии к ней тоже удаляются.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id", nullable = false)
    private Deal deal;
}
