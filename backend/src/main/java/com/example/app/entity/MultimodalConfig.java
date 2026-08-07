package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "multimodal_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalConfig {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36, unique = true)
    private String userId;

    @Column(name = "planner_model", length = 255)
    private String plannerModel;

    @Column(name = "vision_model", length = 255)
    private String visionModel;

    @Column(name = "image_model", length = 255)
    private String imageModel;

    @Column(name = "text_model", length = 255)
    private String textModel;

    @Column(name = "max_steps")
    @Builder.Default
    private Integer maxSteps = 5;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (maxSteps == null) {
            maxSteps = 5;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
