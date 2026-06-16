package com.example.app.service;

import com.example.app.dto.NoteDTO;
import com.example.app.dto.TodoDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String NOTES_KEY = "notes:%s";
    private static final String NOTE_KEY = "note:%s:%s";
    private static final String TODOS_KEY = "todos:%s";
    private static final String TODO_KEY = "todo:%s:%s";
    private static final int LIST_TTL_MINUTES = 5;
    private static final int ITEM_TTL_MINUTES = 10;

    // ==================== Note 缓存操作 ====================

    /**
     * 缓存用户笔记列表
     */
    public void cacheNotes(String userId, List<NoteDTO> notes) {
        try {
            String key = String.format(NOTES_KEY, userId);
            String value = objectMapper.writeValueAsString(notes);
            redisTemplate.opsForValue().set(key, value, LIST_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached notes for user {}", userId);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache notes for user {}", userId, e);
        }
    }

    /**
     * 获取缓存的用户笔记列表
     */
    public List<NoteDTO> getCachedNotes(String userId) {
        try {
            String key = String.format(NOTES_KEY, userId);
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return objectMapper.readValue(value, new TypeReference<List<NoteDTO>>() {});
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to get cached notes for user {}", userId, e);
        }
        return null;
    }

    /**
     * 缓存单条笔记
     */
    public void cacheNote(String userId, NoteDTO note) {
        try {
            String key = String.format(NOTE_KEY, userId, note.getId());
            String value = objectMapper.writeValueAsString(note);
            redisTemplate.opsForValue().set(key, value, ITEM_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached note {} for user {}", note.getId(), userId);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache note {} for user {}", note.getId(), userId, e);
        }
    }

    /**
     * 获取缓存的单条笔记
     */
    public NoteDTO getCachedNote(String userId, String noteId) {
        try {
            String key = String.format(NOTE_KEY, userId, noteId);
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return objectMapper.readValue(value, NoteDTO.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to get cached note {} for user {}", noteId, userId, e);
        }
        return null;
    }

    /**
     * 失效笔记缓存
     */
    public void invalidateNoteCache(String userId, String noteId) {
        String listKey = String.format(NOTES_KEY, userId);
        String itemKey = String.format(NOTE_KEY, userId, noteId);
        redisTemplate.delete(listKey);
        redisTemplate.delete(itemKey);
        log.debug("Invalidated note cache for user {}, note {}", userId, noteId);
    }

    // ==================== Todo 缓存操作 ====================

    /**
     * 缓存用户待办列表
     */
    public void cacheTodos(String userId, List<TodoDTO> todos) {
        try {
            String key = String.format(TODOS_KEY, userId);
            String value = objectMapper.writeValueAsString(todos);
            redisTemplate.opsForValue().set(key, value, LIST_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached todos for user {}", userId);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache todos for user {}", userId, e);
        }
    }

    /**
     * 获取缓存的用户待办列表
     */
    public List<TodoDTO> getCachedTodos(String userId) {
        try {
            String key = String.format(TODOS_KEY, userId);
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return objectMapper.readValue(value, new TypeReference<List<TodoDTO>>() {});
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to get cached todos for user {}", userId, e);
        }
        return null;
    }

    /**
     * 缓存单条待办
     */
    public void cacheTodo(String userId, TodoDTO todo) {
        try {
            String key = String.format(TODO_KEY, userId, todo.getId());
            String value = objectMapper.writeValueAsString(todo);
            redisTemplate.opsForValue().set(key, value, ITEM_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached todo {} for user {}", todo.getId(), userId);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache todo {} for user {}", todo.getId(), userId, e);
        }
    }

    /**
     * 获取缓存的单条待办
     */
    public TodoDTO getCachedTodo(String userId, String todoId) {
        try {
            String key = String.format(TODO_KEY, userId, todoId);
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return objectMapper.readValue(value, TodoDTO.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to get cached todo {} for user {}", todoId, userId, e);
        }
        return null;
    }

    /**
     * 失效待办缓存
     */
    public void invalidateTodoCache(String userId, String todoId) {
        String listKey = String.format(TODOS_KEY, userId);
        String itemKey = String.format(TODO_KEY, userId, todoId);
        redisTemplate.delete(listKey);
        redisTemplate.delete(itemKey);
        log.debug("Invalidated todo cache for user {}, todo {}", userId, todoId);
    }
}