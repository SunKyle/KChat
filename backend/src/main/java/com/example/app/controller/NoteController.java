package com.example.app.controller;

import com.example.app.dto.CreateNoteRequest;
import com.example.app.dto.NoteDTO;
import com.example.app.dto.UpdateNoteRequest;
import com.example.app.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
@Slf4j
public class NoteController {

    private final NoteService noteService;

    /**
     * 获取笔记列表
     * 支持按分类和关键词筛选
     */
    @GetMapping
    public ResponseEntity<List<NoteDTO>> getNotes(
            @RequestParam String userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {

        List<NoteDTO> notes;
        if (keyword != null && !keyword.isEmpty()) {
            notes = noteService.searchNotes(userId, keyword);
        } else if (category != null && !category.isEmpty()) {
            notes = noteService.getNotesByCategory(userId, category);
        } else {
            notes = noteService.getAllNotes(userId);
        }

        return ResponseEntity.ok(notes);
    }

    /**
     * 获取单条笔记
     */
    @GetMapping("/{noteId}")
    public ResponseEntity<NoteDTO> getNote(
            @RequestParam String userId,
            @PathVariable String noteId) {

        NoteDTO note = noteService.getNoteById(userId, noteId);
        return ResponseEntity.ok(note);
    }

    /**
     * 创建笔记
     */
    @PostMapping
    public ResponseEntity<NoteDTO> createNote(
            @RequestParam String userId,
            @RequestBody CreateNoteRequest request) {

        NoteDTO note = noteService.createNote(userId, request);
        log.info("Note created via API: {} for user {}", note.getId(), userId);
        return ResponseEntity.ok(note);
    }

    /**
     * 更新笔记
     */
    @PutMapping("/{noteId}")
    public ResponseEntity<NoteDTO> updateNote(
            @RequestParam String userId,
            @PathVariable String noteId,
            @RequestBody UpdateNoteRequest request) {

        NoteDTO note = noteService.updateNote(userId, noteId, request);
        log.info("Note updated via API: {} for user {}", noteId, userId);
        return ResponseEntity.ok(note);
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @RequestParam String userId,
            @PathVariable String noteId) {

        noteService.deleteNote(userId, noteId);
        log.info("Note deleted via API: {} for user {}", noteId, userId);
        return ResponseEntity.noContent().build();
    }
}