
package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_setting")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSetting {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36, unique = true)
    private String userId;

    @Column(length = 20)
    @Builder.Default
    private String theme = "light";

    @Column(name = "memory_enable")
    @Builder.Default
    private Boolean memoryEnable = true;

    @Column(name = "default_model", length = 255)
    @Builder.Default
    private String defaultModel = "llama3";

    @Column(name = "context_size")
    @Builder.Default
    private Integer contextSize = 10;

    @Column(name = "auto_title")
    @Builder.Default
    private Boolean autoTitle = true;

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
