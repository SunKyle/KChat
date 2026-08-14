package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档实体。
 * 记录用户上传到知识库的原始文档元信息和 Cognee 入库状态。
 */
@Entity
@Table(name = "knowledge_document", indexes = {
        @Index(name = "idx_kdoc_kb_id", columnList = "kb_id"),
        @Index(name = "idx_kdoc_kb_status", columnList = "kb_id, status"),
        @Index(name = "idx_kdoc_created", columnList = "created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "kb_id", nullable = false, length = 36)
    private String kbId;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 200)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** 提取后的纯文本内容 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 文本字符数（提取后） */
    @Column(name = "content_length")
    private Integer contentLength;

    /** Cognee 入库状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProcessingStatus status = ProcessingStatus.PENDING;

    /** 入库失败时的错误信息 */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** Cognee 返回的 data_id */
    @Column(name = "cognee_data_id", length = 100)
    private String cogneeDataId;

    /** 原始文件在服务器上的存储路径（相对 uploads/knowledge 目录，如 {kbId}/{docId}_文件名.docx） */
    @Column(name = "stored_file_path", length = 1000)
    private String storedFilePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ProcessingStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 文档处理状态枚举。
     * PENDING → PROCESSING → INDEXED / FAILED
     */
    public enum ProcessingStatus {
        /** 等待处理 */
        PENDING,
        /** 正在提取文本或写入 Cognee */
        PROCESSING,
        /** 已成功写入 Cognee 知识图谱 */
        INDEXED,
        /** 处理失败 */
        FAILED
    }
}
