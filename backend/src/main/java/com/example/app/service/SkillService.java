package com.example.app.service;

import com.example.app.dto.SkillRequest;
import com.example.app.dto.SkillResponse;
import com.example.app.entity.Skill;
import com.example.app.repository.SkillRepository;
import com.example.app.repository.SkillUsageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Skill 管理服务。
 *
 * <p>职责：
 * <ul>
 *   <li>Skill CRUD（创建/查询/更新/删除）
 *   <li>激活匹配（根据用户消息 + 意图分析结果，手动/关键词/意图三种模式）
 *   <li>JSON 数组字段序列化/反序列化（allowedToolNames 等 List<String> ↔ JSON 字符串）
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>Skill 实体把 List<String> 存为 JSON 字符串（单列存储），Service 层负责转换
 *   <li>激活匹配 MVP 阶段支持手动 + 关键词；意图匹配需要 QueryAnalysisResult，留到 Pipeline 集成时启用
 *   <li>查询时返回 SkillResponse，List<String> 字段已反序列化，前端无需二次解析
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillUsageRepository skillUsageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final TypeReference<List<String>> LIST_STRING = new TypeReference<>() {};

    // ── CRUD ──────────────────────────────────────────────────

    /**
     * 创建 Skill。
     */
    @Transactional
    public SkillResponse create(String userId, SkillRequest request) {
        String id = UUID.randomUUID().toString();
        Skill skill = Skill.builder()
                .id(id)
                .userId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .icon(request.getIcon())
                .systemPromptTemplate(request.getSystemPromptTemplate())
                .systemPromptSupplement(request.getSystemPromptSupplement())
                .allowedToolNamesJson(toJsonList(request.getAllowedToolNames()))
                .forbiddenToolNamesJson(toJsonList(request.getForbiddenToolNames()))
                .triggerKeywordsJson(toJsonList(request.getTriggerKeywords()))
                .triggerIntentTypesJson(toJsonList(request.getTriggerIntentTypes()))
                .inputSchemaJson(request.getInputSchemaJson())
                .outputSchemaJson(request.getOutputSchemaJson())
                .completionHookType(request.getCompletionHookType() != null
                        ? request.getCompletionHookType() : Skill.CompletionHookType.NONE)
                .completionHookParamsJson(request.getCompletionHookParamsJson())
                .maxIterations(request.getMaxIterations() != null ? request.getMaxIterations() : 5)
                .isEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true)
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : false)
                .build();
        Skill saved = skillRepository.save(skill);
        log.info("[SkillService] Created skill: id={}, name={}, user={}", saved.getId(), saved.getName(), userId);
        return toResponse(saved);
    }

    /**
     * 查询用户可见 Skill 列表（自己的 + 公共的）。
     */
    @Transactional(readOnly = true)
    public List<SkillResponse> listByUser(String userId) {
        return skillRepository.findByUserIdOrIsPublicTrueOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询用户自己的 Skill 列表（不含公共）。
     */
    @Transactional(readOnly = true)
    public List<SkillResponse> listOwnByUser(String userId) {
        return skillRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 按 ID 查询用户可用 Skill（自己的或公共的）。
     */
    @Transactional(readOnly = true)
    public SkillResponse getById(String userId, String skillId) {
        Skill skill = skillRepository.findByIdAndUserIdOrIsPublicTrue(skillId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Skill 不存在或无权访问: " + skillId));
        return toResponse(skill);
    }

    /**
     * 按 ID 查询 Skill 实体（内部使用，不做权限校验）。
     * 供 Pipeline Stage 查询激活的 Skill 配置。
     */
    @Transactional(readOnly = true)
    public Skill getEntityById(String skillId) {
        return skillRepository.findById(skillId).orElse(null);
    }

    /**
     * 更新 Skill（只能更新自己的）。
     */
    @Transactional
    public SkillResponse update(String userId, String skillId, SkillRequest request) {
        // 优先查自己的 Skill；找不到再查公共 Skill（避免 OR 返回多条）
        Skill skill = skillRepository.findByIdAndUserId(skillId, userId)
                .orElseGet(() -> skillRepository.findById(skillId)
                        .filter(Skill::getIsPublic)
                        .orElse(null));
        if (skill == null) {
            throw new IllegalArgumentException("Skill 不存在或无权修改: " + skillId);
        }
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skill.setIcon(request.getIcon());
        skill.setSystemPromptTemplate(request.getSystemPromptTemplate());
        skill.setSystemPromptSupplement(request.getSystemPromptSupplement());
        skill.setAllowedToolNamesJson(toJsonList(request.getAllowedToolNames()));
        skill.setForbiddenToolNamesJson(toJsonList(request.getForbiddenToolNames()));
        skill.setTriggerKeywordsJson(toJsonList(request.getTriggerKeywords()));
        skill.setTriggerIntentTypesJson(toJsonList(request.getTriggerIntentTypes()));
        skill.setInputSchemaJson(request.getInputSchemaJson());
        skill.setOutputSchemaJson(request.getOutputSchemaJson());
        skill.setCompletionHookType(request.getCompletionHookType() != null
                ? request.getCompletionHookType() : Skill.CompletionHookType.NONE);
        skill.setCompletionHookParamsJson(request.getCompletionHookParamsJson());
        if (request.getMaxIterations() != null) skill.setMaxIterations(request.getMaxIterations());
        if (request.getIsEnabled() != null) skill.setIsEnabled(request.getIsEnabled());
        if (request.getIsPublic() != null) skill.setIsPublic(request.getIsPublic());
        Skill saved = skillRepository.save(skill);
        log.info("[SkillService] Updated skill: id={}, name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    /**
     * 删除 Skill（只能删除自己的）。
     */
    @Transactional
    public void delete(String userId, String skillId) {
        if (!skillRepository.existsById(skillId)) {
            throw new IllegalArgumentException("Skill 不存在: " + skillId);
        }
        skillRepository.deleteByIdAndUserId(skillId, userId);
        log.info("[SkillService] Deleted skill: id={}, user={}", skillId, userId);
    }

    // ── 激活匹配 ─────────────────────────────────────────────

    /**
     * 按用户消息 + 意图匹配可激活的 Skill（MVP：手动 + 关键词）。
     *
     * <p>匹配优先级：
     * <ol>
     *   <li>手动指定 skillId（请求体带 skillId）→ 直接返回
     *   <li>关键词匹配（用户消息命中 triggerKeywords）→ 返回第一个命中
     *   <li>无匹配 → 返回 null（走默认通用模式）
     * </ol>
     *
     * @param userId      用户 ID
     * @param skillId     手动指定的 Skill ID（可为 null）
     * @param userMessage 用户消息（用于关键词匹配）
     * @return 激活的 Skill，无匹配返回 null
     */
    @Transactional(readOnly = true)
    public Skill matchActiveSkill(String userId, String skillId, String userMessage) {
        // 1. 手动指定优先
        if (skillId != null && !skillId.isBlank()) {
            Skill skill = skillRepository.findByIdAndUserIdOrIsPublicTrue(skillId, userId).orElse(null);
            if (skill != null && Boolean.TRUE.equals(skill.getIsEnabled())) {
                log.info("[SkillService] Skill activated (manual): id={}, name={}", skill.getId(), skill.getName());
                return skill;
            }
        }

        // 2. 关键词匹配
        if (userMessage != null && !userMessage.isBlank()) {
            List<Skill> candidates = skillRepository.findByUserIdOrIsPublicTrueOrderByUpdatedAtDesc(userId);
            for (Skill skill : candidates) {
                if (!Boolean.TRUE.equals(skill.getIsEnabled())) continue;
                List<String> keywords = fromJsonList(skill.getTriggerKeywordsJson());
                if (keywords != null && keywords.stream().anyMatch(k -> k != null && !k.isBlank()
                        && userMessage.toLowerCase().contains(k.toLowerCase()))) {
                    log.info("[SkillService] Skill activated (keyword): id={}, name={}, keyword matched",
                            skill.getId(), skill.getName());
                    return skill;
                }
            }
        }

        // 3. 无匹配
        return null;
    }

    /**
     * 匹配激活的 Skill（返回 Response DTO，供 Controller 直接返回）。
     */
    @Transactional(readOnly = true)
    public SkillResponse matchActiveSkillResponse(String userId, String skillId, String userMessage) {
        Skill skill = matchActiveSkill(userId, skillId, userMessage);
        return skill != null ? toResponse(skill) : null;
    }

    /**
     * 查询用户所有启用的 Skill（供 Orchestrator LLM 路由时构建 SkillSpecification 列表）。
     */
    @Transactional(readOnly = true)
    public List<Skill> listEnabledForUser(String userId) {
        return skillRepository.findByUserIdOrIsPublicTrueOrderByUpdatedAtDesc(userId).stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsEnabled()))
                .collect(Collectors.toList());
    }

    // ── List<String> ↔ JSON 序列化 ──────────────────────────

    private String toJsonList(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("[SkillService] Failed to serialize list: {}", list, e);
            return null;
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, LIST_STRING);
        } catch (Exception e) {
            log.warn("[SkillService] Failed to deserialize list: {}", json, e);
            return new ArrayList<>();
        }
    }

    // ── Entity → Response 转换 ──────────────────────────────

    private SkillResponse toResponse(Skill skill) {
        return SkillResponse.builder()
                .id(skill.getId())
                .userId(skill.getUserId())
                .name(skill.getName())
                .description(skill.getDescription())
                .icon(skill.getIcon())
                .systemPromptTemplate(skill.getSystemPromptTemplate())
                .systemPromptSupplement(skill.getSystemPromptSupplement())
                .allowedToolNames(fromJsonList(skill.getAllowedToolNamesJson()))
                .forbiddenToolNames(fromJsonList(skill.getForbiddenToolNamesJson()))
                .triggerKeywords(fromJsonList(skill.getTriggerKeywordsJson()))
                .triggerIntentTypes(fromJsonList(skill.getTriggerIntentTypesJson()))
                .inputSchemaJson(skill.getInputSchemaJson())
                .outputSchemaJson(skill.getOutputSchemaJson())
                .completionHookType(skill.getCompletionHookType())
                .completionHookParamsJson(skill.getCompletionHookParamsJson())
                .maxIterations(skill.getMaxIterations())
                .isEnabled(skill.getIsEnabled())
                .isPublic(skill.getIsPublic())
                .createdAt(skill.getCreatedAt())
                .updatedAt(skill.getUpdatedAt())
                .build();
    }
}
