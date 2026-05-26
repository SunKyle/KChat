package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "model_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String modelId;

    @Column(nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private String apiKey;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ModelType type;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ModelType {
        OPENAI_COMPATIBLE
    }
}