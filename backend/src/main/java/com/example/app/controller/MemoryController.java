package com.example.app.controller;

import com.example.app.dto.MemoryDTO;
import com.example.app.dto.MemoryRecallRequest;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class MemoryController {

    private final MemoryService memoryService;

    @GetMapping
    public ResponseEntity<List<MemoryDTO>> getMemories(@RequestParam String userId) {
        List<MemoryDTO> memories = memoryService.getAllLongTermMemory(userId);
        return ResponseEntity.ok(memories);
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<MemoryDTO>> getMemoriesByType(
            @RequestParam String userId,
            @PathVariable String type) {
        List<MemoryDTO> memories = memoryService.getLongTermMemoryByType(userId, type);
        return ResponseEntity.ok(memories);
}

@GetMapping("/{id}")
    public ResponseEntity<MemoryDTO> getMemoryById(@PathVariable Long id) {
        return memoryService.getAllLongTermMemory("").stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MemoryDTO> createMemory(@RequestBody MemoryDTO request) {
        if (request.getUserId() == null || request.getContent() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        MemoryType type = request.getMemoryType();
        if (type == null) {
            type = MemoryType.KNOWLEDGE;
        }
        
        MemoryDTO memory = memoryService.saveLongTermMemory(
                request.getUserId(),
                request.getContent(),
                type,
                request.getImportance()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(memory);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<MemoryDTO>> createMemories(@RequestBody List<MemoryDTO> requests) {
        List<MemoryDTO> saved = requests.stream()
                .filter(r -> r.getUserId() != null && r.getContent() != null)
                .map(r -> {
                    MemoryType type = r.getMemoryType();
                    if (type == null) {
                        type = MemoryType.KNOWLEDGE;
                    }
                    return memoryService.saveLongTermMemory(
                            r.getUserId(),
                            r.getContent(),
                            type,
                            r.getImportance()
                    );
                })
                .collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/recall")
    public ResponseEntity<Map<String, Object>> recallMemories(@RequestBody MemoryRecallRequest request) {
        if (request.getUserId() == null || request.getQuery() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        int topK = request.getTopK() != null ? request.getTopK() : 5;
        List<MemoryDTO> memories;
        
        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            memories = memoryService.recallLongTermMemory(request.getUserId(), request.getQuery(), topK, request.getTypes());
        } else {
            memories = memoryService.recallLongTermMemory(request.getUserId(), request.getQuery(), topK);
        }
        
        return ResponseEntity.ok(Map.of(
                "memories", memories,
                "count", memories.size()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMemory(@PathVariable Long id) {
        memoryService.deleteLongTermMemory(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> deleteByUserId(@PathVariable String userId) {
        memoryService.clearAllMemory(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Integer>> cleanupExpired() {
        int deleted = memoryService.cleanExpiredLongTermMemory();
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @GetMapping("/types")
    public ResponseEntity<List<String>> getMemoryTypes() {
        List<String> types = Arrays.stream(MemoryType.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        return ResponseEntity.ok(types);
    }
}