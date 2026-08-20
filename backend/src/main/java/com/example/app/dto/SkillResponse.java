package com.example.app.dto;

import com.example.app.entity.Skill;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 响应 DTO。
 *
 * <p>返回给前端时使用，把实体的 JSON 字符串字段反序列化为 List，
 * 避免前端二次解析。包含 usageCount（调用次数）便于列表展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResponse {

    private String id;
    private String userId;
    private String name;
    private String description;
    private String icon;

    private String systemPromptTemplate;
    private String systemPromptSupplement;

    private List<String> allowedToolNames;
    private List<String> forbiddenToolNames;
    private List<String> triggerKeywords;
    private List<String> triggerIntentTypes;

    private String inputSchemaJson;
    private String outputSchemaJson;

    private Skill.CompletionHookType completionHookType;
    private String completionHookParamsJson;

    private Integer maxIterations;
    private Boolean isEnabled;
    private Boolean isPublic;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 调用次数（统计字段，列表查询时填充） */
    private Long usageCount;
}
