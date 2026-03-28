package com.crm.repository;

import com.crm.model.Task;
import com.crm.model.enums.TaskPriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Репозиторий задач.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByDealId(Long dealId);

    List<Task> findByAssignedToId(Long userId);

    List<Task> findByCompletedFalse();

    List<Task> findByPriorityAndCompletedFalse(TaskPriority priority);

    // Просроченные задачи: due_date < сегодня и не выполнены
    @Query("SELECT t FROM Task t WHERE t.dueDate < :today AND t.completed = false")
    List<Task> findOverdue(@Param("today") LocalDate today);
}
