package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 实体 —— 能力包。
 *
 * <p>Skill 是 Orchestrator 的调度对象，封装一个完整能力：
 * <ul>
 *   <li>专属 systemPrompt（覆盖默认提示词）
 *   <li>工具白名单 allowedToolNames（限制 Skill 内部 Agent 可调用的 Tool）
 *   <li>input/output 契约（用于 Orchestrator LLM 选 Skill，未来用于 DAG 依赖推导）
 *   <li>完成钩子 completionHook（Skill 执行完后的副作用，如写入笔记）
 * </ul>
 *
 * <p>执行语义：Skill 本身不做决策，内部复用 executeWithAgentLoop（ReAct 模式），
 * 由 LLM 自主调用白名单内的 Tool。Orchestrator 层负责选 Skill 和传参。
 *
 * <p>触发方式：
 * <ul>
 *   <li>手动激活：请求体带 skillId → 直接激活
 *   <li>关键词匹配：用户消息命中 keywords → 激活
 *   <li>意图匹配：queryAnalysisResult.intentType 与 intentTypes 交集 → 激活
 *   <li>LLM 路由（Orchestrator 模式）：由顶层 LLM 决定
 * </ul>
 */
@Entity
@Table(name = "skill", indexes = {
    @Index(name = "idx_skill_user_id", columnList = "user_id"),
    @Index(name = "idx_skill_user_updated", columnList = "user_id, updated_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    /** 图标标识（Lucide 图标名） */
    @Column(length = 64)
    private String icon;

    /**
     * 专属 system prompt 模板。
     * 非空 → 完全覆盖默认 system prompt；
     * 为空 → 使用默认 system prompt + systemPromptSupplement 追加。
     * 支持变量：{user_profile} {memory_cognee_graph} {search_context}
     */
    @Column(name = "system_prompt_template", length = 8000)
    private String systemPromptTemplate;

    /** 追加到默认 system prompt 末尾的补充指令（当 systemPromptTemplate 为空时生效） */
    @Column(name = "system_prompt_supplement", length = 4000)
    private String systemPromptSupplement;

    /**
     * 工具白名单（JSON 数组字符串，如 ["webSearch","recallMemory"]）。
     * 非空 → Skill 内部 Agent 只能调用列表内的 Tool；
     * 为空 → 不限制（继承用户全局可用工具）。
     */
    @Column(name = "allowed_tool_names", length = 2000)
    private String allowedToolNamesJson;

    /**
     * 工具黑名单（JSON 数组字符串）。
     * 在白名单基础上进一步排除。
     */
    @Column(name = "forbidden_tool_names", length = 2000)
    private String forbiddenToolNamesJson;

    /**
     * 触发关键词（JSON 数组字符串，如 ["周报","weekly"]）。
     * 用户消息命中任意关键词 → 激活（仅手动/关键词模式用）。
     */
    @Column(name = "trigger_keywords", length = 1000)
    private String triggerKeywordsJson;

    /**
     * 触发意图类型（JSON 数组字符串，如 ["SUMMARIZE","TRANSLATE"]）。
     * 与 QueryAnalysisResult.intentType 交集 → 激活。
     */
    @Column(name = "trigger_intent_types", length = 1000)
    private String triggerIntentTypesJson;

    /**
     * 输入契约（JSON Schema 字符串）。
     * 描述 Skill 接受的参数结构，用于：
     * 1) Orchestrator LLM 选 Skill 时作为 function parameters
     * 2) 未来 DAG 模式做依赖推导
     */
    @Column(name = "input_schema", length = 4000)
    private String inputSchemaJson;

    /**
     * 输出契约（JSON Schema 字符串）。
     * 描述 Skill 产出的数据结构，未来用于 DAG 依赖推导。
     */
    @Column(name = "output_schema", length = 4000)
    private String outputSchemaJson;

    /**
     * 完成钩子类型（CREATE_NOTE / SCHEDULE_REMINDER / SAVE_TO_KB / NONE）。
     * Skill 执行完后由 SkillCompletionHookStage 执行。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "completion_hook_type", length = 32)
    @Builder.Default
    private CompletionHookType completionHookType = CompletionHookType.NONE;

    /** 完成钩子参数（JSON 字符串，结构随 hookType 变化） */
    @Column(name = "completion_hook_params", length = 2000)
    private String completionHookParamsJson;

    /** Skill 内部 Agent 最大迭代次数（默认 5） */
    @Column(name = "max_iterations", nullable = false)
    @Builder.Default
    private Integer maxIterations = 5;

    /** 是否启用 */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    /** 是否公共 Skill（所有用户可见） */
    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isEnabled == null) isEnabled = true;
        if (isPublic == null) isPublic = false;
        if (maxIterations == null) maxIterations = 5;
        if (completionHookType == null) completionHookType = CompletionHookType.NONE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 完成钩子类型枚举。
     * Skill 执行完毕后由 SkillCompletionHookStage 触发的副作用。
     */
    public enum CompletionHookType {
        /** 无钩子 */
        NONE,
        /** 将 Skill 产出写入笔记 */
        CREATE_NOTE,
        /** 创建提醒 */
        SCHEDULE_REMINDER,
        /** 将 Skill 产出存入指定知识库 */
        SAVE_TO_KB
    }
}
