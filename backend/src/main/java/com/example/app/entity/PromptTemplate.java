package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Prompt 模板实体类
 * 
 * 用于存储和管理系统使用的 Prompt 模板，支持版本控制和多模板策略
 */
@Entity
@Table(name = "prompt_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplate {

    /**
     * 模板唯一标识
     */
    @Id
    @Column(length = 36)
    private String id;

    /**
     * 模板名称
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 模板内容（支持占位符）
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 模板描述
     */
    @Column(length = 500)
    private String description;

    /**
     * 模板分类（通用/特定场景）
     */
    @Column(length = 50)
    private String category;

    /**
     * 默认参数（JSON格式）
     */
    @Column(columnDefinition = "JSON")
    private String defaults;

    /**
     * 版本号
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (version == null) {
            version = 1;
        }
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}