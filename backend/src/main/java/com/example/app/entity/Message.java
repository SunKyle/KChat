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

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}