package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "long_term_memory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LongTermMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private MemoryType type;

    @Column(name = "importance")
    @Builder.Default
    private Integer importance = 5;

    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(columnDefinition = "JSON")
    private String metadata;

    @Column(name = "source_conversation_id", length = 36)
    private String sourceConversationId;

    @Column(name = "source_message_id", length = 36)
    private String sourceMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (importance == null) {
            importance = 5;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum MemoryType {
        PROFILE,
        PREFERENCE,
        PROJECT,
        SKILL,
        TASK,
        KNOWLEDGE,
        RELATION,
        EVENT
    }
}