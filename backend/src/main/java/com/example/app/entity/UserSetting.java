
package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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

    /** 工具默认模型映射：工具名 → 模型ID（name:modelId）。空表示自动选择。 */
    @Convert(converter = com.example.app.entity.converter.ToolModelsConverter.class)
    @Column(name = "tool_models", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> toolModels = new HashMap<>();

    /** 工具启用状态映射：工具名 → 是否启用。空表示全部启用（默认）。 */
    @Convert(converter = com.example.app.entity.converter.EnabledToolsConverter.class)
    @Column(name = "enabled_tools", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, Boolean> enabledTools = new HashMap<>();

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
