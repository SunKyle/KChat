package com.example.app.controller;

import com.example.app.dto.CreateTodoRequest;
import com.example.app.dto.TodoDTO;
import com.example.app.dto.UpdateTodoRequest;
import com.example.app.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class TodoController {

    private final TodoService todoService;

    /**
     * 获取待办列表
     * 支持按状态、优先级和关键词筛选
     */
    @GetMapping
    public ResponseEntity<List<TodoDTO>> getTodos(
            @RequestParam String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String keyword) {

        List<TodoDTO> todos;
        if (keyword != null && !keyword.isEmpty()) {
            todos = todoService.searchTodos(userId, keyword);
        } else if (status != null && !status.isEmpty()) {
            todos = todoService.getTodosByStatus(userId, status);
        } else if (priority != null && !priority.isEmpty()) {
            todos = todoService.getTodosByPriority(userId, priority);
        } else {
            todos = todoService.getAllTodos(userId);
        }

        return ResponseEntity.ok(todos);
    }

    /**
     * 获取过期待办
     */
    @GetMapping("/overdue")
    public ResponseEntity<List<TodoDTO>> getOverdueTodos(@RequestParam String userId) {
        List<TodoDTO> todos = todoService.getOverdueTodos(userId);
        return ResponseEntity.ok(todos);
    }

    /**
     * 获取单条待办
     */
    @GetMapping("/{todoId}")
    public ResponseEntity<TodoDTO> getTodo(
            @RequestParam String userId,
            @PathVariable String todoId) {

        TodoDTO todo = todoService.getTodoById(userId, todoId);
        return ResponseEntity.ok(todo);
    }

    /**
     * 创建待办
     */
    @PostMapping
    public ResponseEntity<TodoDTO> createTodo(
            @RequestParam String userId,
            @RequestBody CreateTodoRequest request) {

        TodoDTO todo = todoService.createTodo(userId, request);
        log.info("Todo created via API: {} for user {}", todo.getId(), userId);
        return ResponseEntity.ok(todo);
    }

    /**
     * 更新待办
     */
    @PutMapping("/{todoId}")
    public ResponseEntity<TodoDTO> updateTodo(
            @RequestParam String userId,
            @PathVariable String todoId,
            @RequestBody UpdateTodoRequest request) {

        TodoDTO todo = todoService.updateTodo(userId, todoId, request);
        log.info("Todo updated via API: {} for user {}", todoId, userId);
        return ResponseEntity.ok(todo);
    }

    /**
     * 切换待办状态
     */
    @PatchMapping("/{todoId}/toggle")
    public ResponseEntity<TodoDTO> toggleTodo(
            @RequestParam String userId,
            @PathVariable String todoId) {

        TodoDTO todo = todoService.toggleTodoStatus(userId, todoId);
        log.info("Todo toggled via API: {} for user {}", todoId, userId);
        return ResponseEntity.ok(todo);
    }

    /**
     * 删除待办
     */
    @DeleteMapping("/{todoId}")
    public ResponseEntity<Void> deleteTodo(
            @RequestParam String userId,
            @PathVariable String todoId) {

        todoService.deleteTodo(userId, todoId);
        log.info("Todo deleted via API: {} for user {}", todoId, userId);
        return ResponseEntity.noContent().build();
    }
}