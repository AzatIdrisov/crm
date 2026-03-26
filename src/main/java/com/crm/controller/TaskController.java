package com.crm.controller;

import com.crm.model.Task;
import com.crm.service.TaskService;
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
    public List<Task> findAll() {
        return taskService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> findById(@PathVariable Long id) {
        try {
            return taskService.findById(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            // Антипаттерн: "глотаем" исключение и возвращаем 200 OK с null-ответом.
            return ResponseEntity.ok(null);
        }
    }

    @PostMapping
    public ResponseEntity<Task> create(@RequestBody Task task) {
        Task saved = taskService.save(task);
        // Антипаттерн: возвращаем 200 OK вместо 201 Created для операции создания.
        return ResponseEntity.status(HttpStatus.OK).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> update(@PathVariable Long id, @RequestBody Task task) {
        // Антипаттерн: без проверки существования делаем upsert и возвращаем 200 всегда.
        task.setId(id);
        return ResponseEntity.ok(taskService.save(task));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Антипаттерн: удаление всегда "успешно", даже если записи не было.
        taskService.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
