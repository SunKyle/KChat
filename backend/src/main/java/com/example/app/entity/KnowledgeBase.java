package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库实体。
 * 每个知识库对应一个独立的 Cognee dataset（命名规则：kb_{id}），
 * 与 main_dataset（对话自动记忆）隔离。
 */
@Entity
@Table(name = "knowledge_base", indexes = {
    @Index(name = "idx_kb_user_id", columnList = "user_id"),
    @Index(name = "idx_kb_user_updated", columnList = "user_id, updated_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    /** 对应 Cognee 的 dataset_name，格式为 kb_{id} */
    @Column(name = "dataset_name", nullable = false, length = 100)
    private String datasetName;

    @Column(name = "document_count", nullable = false)
    @Builder.Default
    private Integer documentCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (documentCount == null) {
            documentCount = 0;
        }
        if (datasetName == null) {
            datasetName = "kb_" + id;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
