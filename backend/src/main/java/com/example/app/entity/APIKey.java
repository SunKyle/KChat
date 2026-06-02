package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_key")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APIKey {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "api_key_value", nullable = false, length = 255, unique = true)
    private String key;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used")
    private LocalDateTime lastUsed;

    @Column(columnDefinition = "TEXT")
    private String scopes; // JSON array stored as string

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
