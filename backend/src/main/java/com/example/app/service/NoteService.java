package com.example.app.service;

import com.example.app.dto.CreateNoteRequest;
import com.example.app.dto.NoteDTO;
import com.example.app.dto.UpdateNoteRequest;
import com.example.app.entity.Note;
import com.example.app.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteService {

    private final NoteRepository noteRepository;
    private final CacheService cacheService;
    private final NotificationSseManager notificationSseManager;

    /**
     * 获取用户所有笔记
     */
    public List<NoteDTO> getAllNotes(String userId) {
        // 先从缓存获取
        List<NoteDTO> cached = cacheService.getCachedNotes(userId);
        if (cached != null) {
            log.debug("Notes cache hit for user {}", userId);
            return cached;
        }

        // 缓存未命中，从数据库查询
        List<NoteDTO> notes = noteRepository.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        // 更新缓存
        cacheService.cacheNotes(userId, notes);
        log.debug("Notes cache updated for user {}", userId);
        return notes;
    }

    /**
     * 获取单条笔记
     */
    public NoteDTO getNoteById(String userId, String noteId) {
        // 先从缓存获取
        NoteDTO cached = cacheService.getCachedNote(userId, noteId);
        if (cached != null) {
            log.debug("Note cache hit for user {}, note {}", userId, noteId);
            return cached;
        }

        // 缓存未命中，从数据库查询
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new RuntimeException("Note not found"));

        NoteDTO dto = toDTO(note);
        cacheService.cacheNote(userId, dto);
        return dto;
    }

    /**
     * 创建笔记
     */
    @Transactional
    public NoteDTO createNote(String userId, CreateNoteRequest request) {
        Note note = Note.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(request.getTitle() != null ? request.getTitle() : "无标题")
                .content(request.getContent())
                .category(request.getCategory() != null ? request.getCategory() : "默认")
                .tags(request.getTags() != null ? request.getTags() : List.of())
                .pinned(request.getPinned() != null ? request.getPinned() : false)
                .build();

        note = noteRepository.save(note);
        log.info("Created note {} for user {}", note.getId(), userId);

        // 失效列表缓存
        cacheService.invalidateNoteCache(userId, note.getId());

        // 推送 SSE 通知
        notificationSseManager.push(userId, "data_updated", Map.of("type", "note", "action", "create"));

        return toDTO(note);
    }

    /**
     * 更新笔记
     */
    @Transactional
    public NoteDTO updateNote(String userId, String noteId, UpdateNoteRequest request) {
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new RuntimeException("Note not found"));

        if (request.getTitle() != null) {
            note.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            note.setContent(request.getContent());
        }
        if (request.getCategory() != null) {
            note.setCategory(request.getCategory());
        }
        if (request.getTags() != null) {
            note.setTags(request.getTags());
        }
        if (request.getPinned() != null) {
            note.setPinned(request.getPinned());
        }

        note = noteRepository.save(note);
        log.info("Updated note {} for user {}", noteId, userId);

        // 失效相关缓存
        cacheService.invalidateNoteCache(userId, noteId);

        // 推送 SSE 通知
        notificationSseManager.push(userId, "data_updated", Map.of("type", "note", "action", "update"));

        return toDTO(note);
    }

    /**
     * 删除笔记
     */
    @Transactional
    public void deleteNote(String userId, String noteId) {
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new RuntimeException("Note not found"));

        noteRepository.delete(note);
        log.info("Deleted note {} for user {}", noteId, userId);

        // 失效相关缓存
        cacheService.invalidateNoteCache(userId, noteId);

        // 推送 SSE 通知
        notificationSseManager.push(userId, "data_updated", Map.of("type", "note", "action", "delete"));
    }

    /**
     * 搜索笔记
     */
    public List<NoteDTO> searchNotes(String userId, String keyword) {
        return noteRepository.searchByUserIdAndKeyword(userId, keyword)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按分类查询笔记
     */
    public List<NoteDTO> getNotesByCategory(String userId, String category) {
        return noteRepository.findByUserIdAndCategory(userId, category)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 实体转DTO
     */
    private NoteDTO toDTO(Note note) {
        return NoteDTO.builder()
                .id(note.getId())
                .userId(note.getUserId())
                .title(note.getTitle())
                .content(note.getContent())
                .category(note.getCategory())
                .tags(note.getTags())
                .pinned(note.getPinned())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}