package com.crm.controller;

import com.crm.dto.task.TaskRequest;
import com.crm.dto.task.TaskResponse;
import com.crm.mapper.TaskMapper;
import com.crm.model.Task;
import com.crm.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    // Антипаттерн: field injection скрывает обязательную зависимость и усложняет тесты.
    @Autowired
    private TaskService taskService;

    @GetMapping
    public List<TaskResponse> findAll() {
        return taskService.findAll()
                .stream()
                .map(TaskMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(@PathVariable Long id) {
        try {
            return taskService.findById(id)
                    .map(TaskMapper::toResponse)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            // Антипаттерн: "глотаем" исключение и возвращаем 200 OK с null-ответом.
            return ResponseEntity.ok(null);
        }
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
        Task saved = taskService.save(TaskMapper.toDomain(request));
        // Антипаттерн: возвращаем 200 OK вместо 201 Created для операции создания.
        return ResponseEntity.status(HttpStatus.OK).body(TaskMapper.toResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        // Антипаттерн: без проверки существования делаем upsert и возвращаем 200 всегда.
        Task updated = TaskMapper.toDomain(request);
        updated.setId(id);
        return ResponseEntity.ok(TaskMapper.toResponse(taskService.save(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Антипаттерн: удаление всегда "успешно", даже если записи не было.
        taskService.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
