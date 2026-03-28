package com.crm.model;

import com.crm.model.enums.TaskPriority;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * JPA-сущность задачи.
 *
 * Нюанс: @Column(name = "due_date") + LocalDate маппится в DATE (без времени).
 * Для TIMESTAMP использовать LocalDateTime.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Task extends BaseEntity<Long> {

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TaskPriority priority;

    // Task — владелец связи: в таблице tasks есть FK-колонка deal_id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @Column(name = "due_date")
    private LocalDate dueDate;

    // boolean примитив: Hibernate маппит в BOOLEAN/TINYINT(1).
    // columnDefinition = "BOOLEAN DEFAULT FALSE" — явная дефолтная DDL-константа.
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean completed;
}
