package com.example.app.service;

import com.example.app.dto.CreateTodoRequest;
import com.example.app.dto.TodoDTO;
import com.example.app.dto.UpdateTodoRequest;
import com.example.app.entity.Todo;
import com.example.app.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TodoService {

    private final TodoRepository todoRepository;
    private final CacheService cacheService;

    /**
     * 获取用户所有待办
     */
    public List<TodoDTO> getAllTodos(String userId) {
        // 先从缓存获取
        List<TodoDTO> cached = cacheService.getCachedTodos(userId);
        if (cached != null) {
            log.debug("Todos cache hit for user {}", userId);
            return cached;
        }

        // 缓存未命中，从数据库查询
        List<TodoDTO> todos = todoRepository.findByUserIdOrderByStatusAscPriorityDescUpdatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        // 更新缓存
        cacheService.cacheTodos(userId, todos);
        log.debug("Todos cache updated for user {}", userId);
        return todos;
    }

    /**
     * 获取单条待办
     */
    public TodoDTO getTodoById(String userId, String todoId) {
        // 先从缓存获取
        TodoDTO cached = cacheService.getCachedTodo(userId, todoId);
        if (cached != null) {
            log.debug("Todo cache hit for user {}, todo {}", userId, todoId);
            return cached;
        }

        // 缓存未命中，从数据库查询
        Todo todo = todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new RuntimeException("Todo not found"));

        TodoDTO dto = toDTO(todo);
        cacheService.cacheTodo(userId, dto);
        return dto;
    }

    /**
     * 创建待办
     */
    @Transactional
    public TodoDTO createTodo(String userId, CreateTodoRequest request) {
        Todo todo = Todo.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(request.getTitle() != null ? request.getTitle() : "未命名待办")
                .description(request.getDescription())
                .status("pending")
                .priority(request.getPriority() != null ? request.getPriority() : "medium")
                .dueDate(request.getDueDate())
                .category(request.getCategory() != null ? request.getCategory() : "默认")
                .build();

        todo = todoRepository.save(todo);
        log.info("Created todo {} for user {}", todo.getId(), userId);

        // 失效列表缓存
        cacheService.invalidateTodoCache(userId, todo.getId());

        return toDTO(todo);
    }

    /**
     * 更新待办
     */
    @Transactional
    public TodoDTO updateTodo(String userId, String todoId, UpdateTodoRequest request) {
        Todo todo = todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new RuntimeException("Todo not found"));

        if (request.getTitle() != null) {
            todo.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            todo.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            todo.setStatus(request.getStatus());
            if ("completed".equals(request.getStatus())) {
                todo.setCompletedAt(LocalDateTime.now());
            } else {
                todo.setCompletedAt(null);
            }
        }
        if (request.getPriority() != null) {
            todo.setPriority(request.getPriority());
        }
        if (request.getDueDate() != null) {
            todo.setDueDate(request.getDueDate());
        }
        if (request.getCategory() != null) {
            todo.setCategory(request.getCategory());
        }

        todo = todoRepository.save(todo);
        log.info("Updated todo {} for user {}", todoId, userId);

        // 失效相关缓存
        cacheService.invalidateTodoCache(userId, todoId);

        return toDTO(todo);
    }

    /**
     * 切换待办状态
     */
    @Transactional
    public TodoDTO toggleTodoStatus(String userId, String todoId) {
        Todo todo = todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new RuntimeException("Todo not found"));

        String newStatus = "pending".equals(todo.getStatus()) ? "completed" : "pending";
        todo.setStatus(newStatus);
        todo.setCompletedAt("completed".equals(newStatus) ? LocalDateTime.now() : null);

        todo = todoRepository.save(todo);
        log.info("Toggled todo {} status to {} for user {}", todoId, newStatus, userId);

        // 失效相关缓存
        cacheService.invalidateTodoCache(userId, todoId);

        return toDTO(todo);
    }

    /**
     * 删除待办
     */
    @Transactional
    public void deleteTodo(String userId, String todoId) {
        Todo todo = todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new RuntimeException("Todo not found"));

        todoRepository.delete(todo);
        log.info("Deleted todo {} for user {}", todoId, userId);

        // 失效相关缓存
        cacheService.invalidateTodoCache(userId, todoId);
    }

    /**
     * 搜索待办
     */
    public List<TodoDTO> searchTodos(String userId, String keyword) {
        return todoRepository.searchByUserIdAndKeyword(userId, keyword)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按状态查询待办
     */
    public List<TodoDTO> getTodosByStatus(String userId, String status) {
        return todoRepository.findByUserIdAndStatus(userId, status)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按优先级查询待办
     */
    public List<TodoDTO> getTodosByPriority(String userId, String priority) {
        return todoRepository.findByUserIdAndPriority(userId, priority)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取过期待办
     */
    public List<TodoDTO> getOverdueTodos(String userId) {
        return todoRepository.findOverdueTodos(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 实体转DTO
     */
    private TodoDTO toDTO(Todo todo) {
        return TodoDTO.builder()
                .id(todo.getId())
                .userId(todo.getUserId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .status(todo.getStatus())
                .priority(todo.getPriority())
                .dueDate(todo.getDueDate())
                .category(todo.getCategory())
                .createdAt(todo.getCreatedAt())
                .updatedAt(todo.getUpdatedAt())
                .completedAt(todo.getCompletedAt())
                .build();
    }
}