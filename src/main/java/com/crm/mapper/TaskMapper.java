package com.crm.mapper;

import com.crm.dto.task.TaskRequest;
import com.crm.dto.task.TaskResponse;
import com.crm.model.Task;

public final class TaskMapper {

    private TaskMapper() {}

    public static Task toDomain(TaskRequest request) {
        return Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .dueDate(request.getDueDate())
                .completed(request.isCompleted())
                .build();
    }

    public static TaskResponse toResponse(Task task) {
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setTitle(task.getTitle());
        response.setDescription(task.getDescription());
        response.setPriority(task.getPriority());
        response.setDueDate(task.getDueDate());
        response.setCompleted(task.isCompleted());
        return response;
    }
}
