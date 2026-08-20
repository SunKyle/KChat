package com.example.app.controller;

import com.example.app.dto.SkillRequest;
import com.example.app.dto.SkillResponse;
import com.example.app.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Skill 管理 REST API。
 *
 * <p>端点：
 * <ul>
 *   <li>POST   /api/skills          — 创建 Skill</li>
 *   <li>GET    /api/skills          — 列表（自己的 + 公共的）</li>
 *   <li>GET    /api/skills/{id}     — 详情</li>
 *   <li>PUT    /api/skills/{id}     — 更新</li>
 *   <li>DELETE /api/skills/{id}     — 删除</li>
 *   <li>POST   /api/skills/match    — 匹配激活的 Skill（手动/关键词）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class SkillController {

    private final SkillService skillService;

    @PostMapping
    public ResponseEntity<SkillResponse> create(
            @RequestParam String userId,
            @RequestBody SkillRequest request) {
        log.info("[SkillController] Create: user={}, name={}", userId, request.getName());
        return ResponseEntity.ok(skillService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<SkillResponse>> list(@RequestParam String userId) {
        return ResponseEntity.ok(skillService.listByUser(userId));
    }

    @GetMapping("/{skillId}")
    public ResponseEntity<SkillResponse> getById(
            @RequestParam String userId,
            @PathVariable String skillId) {
        return ResponseEntity.ok(skillService.getById(userId, skillId));
    }

    @PutMapping("/{skillId}")
    public ResponseEntity<SkillResponse> update(
            @RequestParam String userId,
            @PathVariable String skillId,
            @RequestBody SkillRequest request) {
        log.info("[SkillController] Update: user={}, id={}", userId, skillId);
        return ResponseEntity.ok(skillService.update(userId, skillId, request));
    }

    @DeleteMapping("/{skillId}")
    public ResponseEntity<Void> delete(
            @RequestParam String userId,
            @PathVariable String skillId) {
        log.info("[SkillController] Delete: user={}, id={}", userId, skillId);
        skillService.delete(userId, skillId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 匹配激活的 Skill。
     *
     * @param userId      用户 ID
     * @param skillId     手动指定的 Skill ID（可为空）
     * @param userMessage 用户消息（用于关键词匹配，可为空）
     * @return 匹配到的 Skill，无匹配返回 204
     */
    @PostMapping("/match")
    public ResponseEntity<SkillResponse> match(
            @RequestParam String userId,
            @RequestParam(required = false) String skillId,
            @RequestParam(required = false) String userMessage) {
        SkillResponse matched = skillService.matchActiveSkillResponse(userId, skillId, userMessage);
        return matched != null ? ResponseEntity.ok(matched) : ResponseEntity.noContent().build();
    }
}
