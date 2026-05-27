
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
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String type = "default";

    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "source_conversation_id", length = 36)
    private String sourceConversationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
