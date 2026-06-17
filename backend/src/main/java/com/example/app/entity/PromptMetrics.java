package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Prompt 构建监控指标实体
 * 
 * 用于记录每次 Prompt 构建的关键指标，便于监控和分析
 */
@Entity
@Table(name = "prompt_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptMetrics {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 对话 ID
     */
    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    /**
     * 使用的模板版本
     */
    @Column(name = "prompt_version")
    private Integer promptVersion;

    /**
     * Token 总数
     */
    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;

    /**
     * 记忆片段数量
     */
    @Column(name = "memory_count", nullable = false)
    private Integer memoryCount;

    /**
     * 构建耗时（毫秒）
     */
    @Column(name = "build_duration_ms", nullable = false)
    private Long buildDurationMs;

    /**
     * 是否发生截断
     */
    @Column(name = "truncation_occurred", nullable = false)
    private Boolean truncationOccurred;

    /**
     * 截断前 Token 数
     */
    @Column(name = "tokens_before_truncation")
    private Integer tokensBeforeTruncation;

    /**
     * 截断后 Token 数
     */
    @Column(name = "tokens_after_truncation")
    private Integer tokensAfterTruncation;

    /**
     * 用户 ID
     */
    @Column(name = "user_id", length = 36)
    private String userId;

    /**
     * 使用的模型名称
     */
    @Column(name = "model_name", length = 100)
    private String modelName;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}