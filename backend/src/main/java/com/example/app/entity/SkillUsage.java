package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SkillUsage 实体 —— 记录每次 Skill 调用。
 *
 * <p>由 SkillCompletionHookStage 写入，用于：
 * <ul>
 *   <li>调试与回放：inputsSnapshot/outputsSnapshot 保留每次调用快照
 *   <li>使用统计：按 Skill/用户/时间维度统计调用次数与成功率
 *   <li>成本分析：配合 token 计费估算 Skill 级成本
 * </ul>
 */
@Entity
@Table(name = "skill_usage", indexes = {
    @Index(name = "idx_su_skill_id", columnList = "skill_id"),
    @Index(name = "idx_su_user_id", columnList = "user_id"),
    @Index(name = "idx_su_conversation_id", columnList = "conversation_id"),
    @Index(name = "idx_su_started_at", columnList = "started_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false, length = 36)
    private String skillId;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "conversation_id", length = 36)
    private String conversationId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /**
     * 调用状态。
     * RUNNING → 调用方在 push 帧时写入；
     * COMPLETED/FAILED/PARTIAL → SkillCompletionHookStage 在 pop 帧后更新。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private UsageStatus status = UsageStatus.RUNNING;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** Orchestrator 传入的参数快照（JSON 字符串） */
    @Column(name = "inputs_snapshot", length = 4000)
    private String inputsSnapshot;

    /** Skill 产出快照（JSON 字符串，截断保护） */
    @Column(name = "outputs_snapshot", length = 4000)
    private String outputsSnapshot;

    /** 失败时的错误信息（status=FAILED 时有值） */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** Agent 内部迭代次数（Skill 内部 LLM 调 Tool 的轮数） */
    @Column(name = "agent_iterations")
    private Integer agentIterations;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (status == null) status = UsageStatus.RUNNING;
    }

    public enum UsageStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        PARTIAL
    }
}
