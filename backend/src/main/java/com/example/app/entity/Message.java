package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    private String images;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    @Column(columnDefinition = "TEXT")
    private String artifacts;

    @Column(name = "token_count")
    @Builder.Default
    private Integer tokenCount = 0;

    @Column(name = "agent_thinking", columnDefinition = "LONGTEXT")
    private String agentThinking;

    /** 该消息引用的知识库名称列表（JSON 数组），供前端展示"引用来源"标签 */
    @Column(name = "kb_references", columnDefinition = "TEXT")
    private String kbReferences;

    /** 用户消息引用的资源（知识库 / 技能，JSON 数组 of MessageReference），供历史会话展示"当时引用了什么" */
    @Column(name = "msg_references", columnDefinition = "TEXT")
    private String references;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
