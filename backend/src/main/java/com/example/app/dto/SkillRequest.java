package com.example.app.dto;

import com.example.app.entity.Skill;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建/更新 Skill 的请求 DTO。
 *
 * <p>前端用同一份表单做创建和更新（更新时调 PUT 接口）。
 * JSON 数组字段（allowedToolNames / triggerKeywords 等）在 DTO 中用 List，
 * Service 层负责序列化为 JSON 字符串存入实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillRequest {

    @NotBlank(message = "Skill 名称不能为空")
    @Size(max = 100, message = "名称不能超过100字符")
    private String name;

    @Size(max = 1000, message = "描述不能超过1000字符")
    private String description;

    @Size(max = 64)
    private String icon;

    @Size(max = 8000)
    private String systemPromptTemplate;

    @Size(max = 4000)
    private String systemPromptSupplement;

    /** 工具白名单（为空表示不限制） */
    private List<String> allowedToolNames;

    /** 工具黑名单 */
    private List<String> forbiddenToolNames;

    /** 触发关键词 */
    private List<String> triggerKeywords;

    /** 触发意图类型 */
    private List<String> triggerIntentTypes;

    /** 输入契约（JSON Schema） */
    private String inputSchemaJson;

    /** 输出契约（JSON Schema） */
    private String outputSchemaJson;

    /** 完成钩子类型 */
    private Skill.CompletionHookType completionHookType;

    /** 完成钩子参数（JSON） */
    private String completionHookParamsJson;

    /** Skill 内部 Agent 最大迭代次数 */
    @Builder.Default
    private Integer maxIterations = 5;

    /** 是否启用 */
    @Builder.Default
    private Boolean isEnabled = true;

    /** 是否公共 Skill */
    @Builder.Default
    private Boolean isPublic = false;
}
