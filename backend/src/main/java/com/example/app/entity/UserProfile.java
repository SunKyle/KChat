package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profile")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36, unique = true)
    private String userId;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(length = 500)
    private String avatar;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // Preferences stored as JSON-like fields
    @Column(name = "theme", length = 20)
    @Builder.Default
    private String theme = "light";

    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "zh-CN";

    @Column(name = "notification_message")
    @Builder.Default
    private Boolean notificationMessage = true;

    @Column(name = "notification_email")
    @Builder.Default
    private Boolean notificationEmail = false;

    @Column(name = "notification_push")
    @Builder.Default
    private Boolean notificationPush = true;

    @Column(name = "notification_sound")
    @Builder.Default
    private Boolean notificationSound = true;

    // Privacy settings
    @Column(name = "online_status")
    @Builder.Default
    private Boolean onlineStatus = true;

    @Column(name = "message_history")
    @Builder.Default
    private Boolean messageHistory = true;

    @Column(name = "read_receipts")
    @Builder.Default
    private Boolean readReceipts = true;

    @Column(name = "typing_indicator")
    @Builder.Default
    private Boolean typingIndicator = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
