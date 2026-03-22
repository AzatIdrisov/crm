package com.crm.model;

import com.crm.model.enums.TaskPriority;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task extends BaseEntity<Long> {

    private String title;
    private String description;
    private TaskPriority priority;
    private Deal deal;
    private User assignedTo;
    private LocalDate dueDate;
    private boolean completed;
}
