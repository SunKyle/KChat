package com.example.app.controller;

import com.example.app.dto.MemoryDTO;
import com.example.app.dto.MemoryRecallRequest;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.service.LongTermMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 记忆管理 API 控制器
 * 
 * <功能说明>
 * - 核心职责：提供长期记忆的 CRUD 和语义召回功能
 * - 设计模式：RESTful 控制器模式
 * - 依赖关系：依赖 LongTermMemoryService
 * 
 * <API 端点>
 * - GET /api/memories - 获取用户所有记忆
 * - GET /api/memories/{id} - 获取单个记忆详情
 * - GET /api/memories/type/{type} - 按类型获取记忆
 * - GET /api/memories/types - 获取所有记忆类型
 * - POST /api/memories - 创建记忆
 * - POST /api/memories/batch - 批量创建记忆
 * - POST /api/memories/recall - 语义召回记忆
 * - DELETE /api/memories/{id} - 删除单个记忆
 * - DELETE /api/memories/user/{userId} - 删除用户所有记忆
 * - DELETE /api/memories/cleanup - 清理过期记忆
 */
@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class MemoryController {

    /**
     * 长期记忆服务，提供记忆的 CRUD 和语义召回功能
     */
    private final LongTermMemoryService longTermMemoryService;

    /**
     * 获取用户所有记忆
     * 
     * @param userId 用户 ID
     * @return 记忆列表
     */
    @GetMapping
    public ResponseEntity<List<MemoryDTO>> getMemories(@RequestParam String userId) {
        List<MemoryDTO> memories = longTermMemoryService.findByUserId(userId);
        return ResponseEntity.ok(memories);
    }

    /**
     * 按类型获取用户记忆
     * 
     * @param userId 用户 ID
     * @param type 记忆类型
     * @return 记忆列表
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<MemoryDTO>> getMemoriesByType(
            @RequestParam String userId,
            @PathVariable String type) {
        List<MemoryDTO> memories = longTermMemoryService.findByUserIdAndType(userId, type);
        return ResponseEntity.ok(memories);
    }

    /**
     * 获取单个记忆详情
     * 
     * @param id 记忆 ID
     * @return 记忆详情或 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<MemoryDTO> getMemoryById(@PathVariable Long id) {
        return longTermMemoryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建单个记忆
     * 
     * @param request 记忆数据
     * @return 创建的记忆
     */
    @PostMapping
    public ResponseEntity<MemoryDTO> createMemory(@RequestBody MemoryDTO request) {
        if (request.getUserId() == null || request.getContent() == null) {
            return ResponseEntity.badRequest().build();
        }

        MemoryType type = request.getMemoryType();
        if (type == null) {
            type = MemoryType.KNOWLEDGE;
        }

        request.setType(type.name());
        MemoryDTO memory = longTermMemoryService.save(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(memory);
    }

    /**
     * 批量创建记忆
     * 
     * @param requests 记忆数据列表
     * @return 创建的记忆列表
     */
    @PostMapping("/batch")
    public ResponseEntity<List<MemoryDTO>> createMemories(@RequestBody List<MemoryDTO> requests) {
        requests.forEach(r -> {
            if (r.getMemoryType() == null) {
                r.setType(MemoryType.KNOWLEDGE.name());
            }
        });
        List<MemoryDTO> saved = longTermMemoryService.saveAll(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 语义召回记忆
     * 
     * 根据查询文本的语义相似度召回相关记忆。
     * 
     * @param request 召回请求，包含用户 ID、查询文本、topK 和可选的类型过滤
     * @return 召回结果，包含记忆列表和数量
     */
    @PostMapping("/recall")
    public ResponseEntity<Map<String, Object>> recallMemories(@RequestBody MemoryRecallRequest request) {
        if (request.getUserId() == null || request.getQuery() == null) {
            return ResponseEntity.badRequest().build();
        }

        int topK = request.getTopK() != null ? request.getTopK() : 5;
        List<MemoryDTO> memories;

        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            memories = longTermMemoryService.recall(request.getUserId(), request.getQuery(), topK, request.getTypes());
        } else {
            memories = longTermMemoryService.recall(request.getUserId(), request.getQuery(), topK);
        }

        return ResponseEntity.ok(Map.of(
                "memories", memories,
                "count", memories.size()));
    }

    /**
     * 删除单个记忆
     * 
     * @param id 记忆 ID
     * @return 204 删除成功
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMemory(@PathVariable Long id) {
        longTermMemoryService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除用户所有记忆
     * 
     * @param userId 用户 ID
     * @return 204 删除成功
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> deleteByUserId(@PathVariable String userId) {
        longTermMemoryService.deleteByUserId(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 清理过期记忆
     * 
     * @return 清理结果，包含删除数量
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Integer>> cleanupExpired() {
        int deleted = longTermMemoryService.cleanExpired();
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    /**
     * 获取所有记忆类型
     * 
     * @return 记忆类型列表
     */
    @GetMapping("/types")
    public ResponseEntity<List<String>> getMemoryTypes() {
        List<String> types = Arrays.stream(MemoryType.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        return ResponseEntity.ok(types);
    }
}
