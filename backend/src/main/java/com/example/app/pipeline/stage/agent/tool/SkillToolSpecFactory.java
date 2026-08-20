package com.example.app.pipeline.stage.agent.tool;

import com.example.app.entity.Skill;
import com.example.app.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SkillToolSpecFactory —— 将 Skill 编译成 Orchestrator LLM 可调用的伪 ToolSpecification。
 *
 * <p>在 "Skill = 可调用能力" 的双层 ReAct 架构中：
 * <ul>
 *   <li>顶层 Orchestrator LLM 不直接看到原子 Tool（createReminder、createTodo 等）</li>
 *   <li>它只看到一组 Skill 伪 function：每个 Skill 对应一个 {@code call_skill_<skillId>(...)} ToolSpec</li>
 *   <li>当 LLM 调 {@code call_skill_定时提醒({instruction: "明天3点开Boss会"})} 时，
 *       {@link com.example.app.service.tool.SkillExecutor} push 一个 SPECIALIST 帧，
 *       在该帧内部走标准的"原子 Tool ReAct"嵌套循环，
 *       完成后 pop 并把结果回填为 Orchestrator 这一轮的 Observation</li>
 * </ul>
 *
 * <p>伪 ToolSpec 设计：
 * <ul>
 *   <li>Tool 名：{@code call_skill_<skillId>}（前缀统一，ToolInvocationStage 分发时用前缀判断）</li>
 *   <li>parameters：优先使用 {@link Skill#getInputSchemaJson()} 解析成 JsonObjectSchema；
 *       解析失败/为空时回退为 {@code {instruction: string}}（把自然语言 instruction 作为唯一入参，足够 MVP）</li>
 *   <li>description：拼接 Skill.description + Skill.triggerKeywords 示例 + 反例边界，
 *       让 Orchestrator LLM 在 ReAct 时准确路由（这一层的路由正确性靠 description 文案）</li>
 * </ul>
 *
 * <p>注意：本类是纯函数式工厂（无状态），不注册为 Spring Bean，
 * 由调用方（ToolDefinitionStage）直接实例化或用静态方法。
 * 目前做成 Spring Component + SkillService 依赖，便于根据用户 ID 动态过滤可用 Skill。
 */
@Slf4j
public class SkillToolSpecFactory {

    private static final TypeReference<Map<String, Object>> MAP_OBJECT = new TypeReference<>() {};
    private static final String CALL_SKILL_PREFIX = "call_skill_";

    /** 伪 function 前缀的统一长度，后续分发用 {@code startsWith(CALL_SKILL_PREFIX)} 判断 */
    public static String skillCallPrefix() {
        return CALL_SKILL_PREFIX;
    }

    /** 从伪 function 名里还原 skillId；如不匹配返回 null */
    public static String extractSkillId(String toolName) {
        if (toolName == null || !toolName.startsWith(CALL_SKILL_PREFIX)) return null;
        return toolName.substring(CALL_SKILL_PREFIX.length());
    }

    /**
     * 批量编译：将一组启用的 Skill 转为 Orchestrator 层使用的 ToolSpecification 列表。
     */
    public static List<ToolSpecification> build(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) return List.of();
        List<ToolSpecification> result = new ArrayList<>(skills.size());
        for (Skill skill : skills) {
            try {
                result.add(buildOne(skill));
            } catch (Exception e) {
                log.warn("[SkillToolSpec] Failed to build spec for skill id={}, name={}: {}",
                        skill.getId(), skill.getName(), e.getMessage());
            }
        }
        return result;
    }

    /** 单个 Skill → ToolSpecification */
    public static ToolSpecification buildOne(Skill skill) {
        String toolName = CALL_SKILL_PREFIX + skill.getId();
        String description = buildDescription(skill);
        JsonObjectSchema parameters = buildParameters(skill);

        return ToolSpecification.builder()
                .name(toolName)
                .description(description)
                .parameters(parameters)
                .build();
    }

    // ── description 合成 ────────────────────────────────────────

    /**
     * 合成让 Orchestrator LLM 正确路由的 description。
     *
     * <p>格式：
     * <pre>
     * 【技能：{name}】{description}
     *  触发场景示例：关键词/语义符合以下之一时使用：kw1, kw2, kw3
     *  不要在下列场景使用：{反例提示（从 description/关键词推断不到就写"无明显边界时，根据用户需求的核心是否是本 Skill 的职责判断"）}
     *  输入说明：instruction 字段填写给这个 Skill 的自然语言指令，包含足够上下文让它独立执行。
     * </pre>
     */
    private static String buildDescription(Skill skill) {
        List<String> keywords = parseStringList(skill.getTriggerKeywordsJson());
        StringBuilder sb = new StringBuilder();
        sb.append("【技能：").append(nullSafe(skill.getName())).append("】");
        if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
            sb.append(skill.getDescription().trim());
        }
        if (!keywords.isEmpty()) {
            sb.append("\n当用户需求语义或关键词命中以下任一项时优先使用本技能：")
              .append(String.join("、", keywords)).append("。");
        }
        List<String> intentTypes = parseStringList(skill.getTriggerIntentTypesJson());
        if (!intentTypes.isEmpty()) {
            sb.append("\n匹配的意图类型：").append(String.join("、", intentTypes)).append("。");
        }
        sb.append("\n【使用方法】入参 instruction 为给本技能的自然语言任务描述，需包含足够上下文让它独立完成子任务（如提醒时间、待办内容等），无需再追问用户。");
        sb.append("\n一次调用只完成一个子任务；若用户有多个不同领域的子任务，请分多次调用不同的 Skill。");
        return sb.toString();
    }

    // ── parameters 合成 ──────────────────────────────────────────

    /**
     * 优先用 Skill.inputSchemaJson（JSON Schema object），解析失败/空时退化为 {instruction: string}。
     *
     * <p>注意：LangChain4j 1.4.0 的 JsonObjectSchema 期望 properties + required，
     * 这里做"防御性解析"：如果用户在 Skill 管理页面写的 JSON Schema 格式不标准，
     * 不要让整条链路崩溃，静默退化。
     */
    private static JsonObjectSchema buildParameters(Skill skill) {
        JsonObjectSchema fromSchema = tryParseInputSchema(skill.getInputSchemaJson());
        if (fromSchema != null) return fromSchema;
        // 兜底：instruction 字符串入参
        JsonStringSchema instructionField = JsonStringSchema.builder()
                .description("给这个技能的自然语言任务指令，必须包含足够上下文让技能独立完成（例如'明天下午3点提醒我和Boss开会'、'添加一个写周报的工作待办，截止后天'）")
                .build();
        return JsonObjectSchema.builder()
                .addProperty("instruction", instructionField)
                .required("instruction")
                .additionalProperties(false)
                .build();
    }

    /**
     * 尝试把 Skill.inputSchemaJson 转为 JsonObjectSchema。
     * 仅支持 MVP 级别的子集：object { properties: {name:{type:string,description:...}}, required:[...] }。
     * 不支持递归嵌套（嵌套 object/array 统一退化，或者返回 null 触发兜底 schema）。
     */
    private static JsonObjectSchema tryParseInputSchema(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> root = JsonUtils.fromJson(json, Object.class) instanceof Map
                    ? (Map<String, Object>) JsonUtils.fromJson(json, Object.class)
                    : null;
            if (root == null) return null;
            Object props = root.get("properties");
            if (!(props instanceof Map)) return null;
            JsonObjectSchema.Builder b = JsonObjectSchema.builder();
            List<String> requiredList = new ArrayList<>();
            Object reqRaw = root.get("required");
            if (reqRaw instanceof List<?> list) {
                for (Object o : list) if (o != null) requiredList.add(String.valueOf(o));
            }
            Map<String, Object> propsMap = (Map<String, Object>) props;
            for (Map.Entry<String, Object> e : propsMap.entrySet()) {
                String fieldName = e.getKey();
                Object spec = e.getValue();
                JsonSchemaElement field = toJsonSchemaElement(spec);
                if (field == null) field = JsonStringSchema.builder().build();
                b.addProperty(fieldName, field);
            }
            if (!requiredList.isEmpty()) b.required(requiredList);
            Object addProps = root.get("additionalProperties");
            if (addProps instanceof Boolean bo) b.additionalProperties(bo);
            return b.build();
        } catch (Exception ex) {
            log.debug("[SkillToolSpec] parse inputSchema failed, fallback to instruction-only: {}",
                    ex.getMessage());
            return null;
        }
    }

    private static JsonSchemaElement toJsonSchemaElement(Object spec) {
        if (!(spec instanceof Map m)) return null;
        Object type = m.get("type");
        String desc = m.get("description") != null ? String.valueOf(m.get("description")) : null;
        if ("string".equals(type)) {
            JsonStringSchema.Builder b = JsonStringSchema.builder();
            if (desc != null) b.description(desc);
            return b.build();
        }
        if ("integer".equals(type)) {
            var b = dev.langchain4j.model.chat.request.json.JsonIntegerSchema.builder();
            if (desc != null) b.description(desc);
            return b.build();
        }
        if ("number".equals(type)) {
            var b = dev.langchain4j.model.chat.request.json.JsonNumberSchema.builder();
            if (desc != null) b.description(desc);
            return b.build();
        }
        if ("boolean".equals(type)) {
            var b = dev.langchain4j.model.chat.request.json.JsonBooleanSchema.builder();
            if (desc != null) b.description(desc);
            return b.build();
        }
        // 其他：默认按 string 处理（不支持嵌套避免复杂度）
        JsonStringSchema.Builder b = JsonStringSchema.builder();
        if (desc != null) b.description(desc);
        return b.build();
    }

    // ── utils ────────────────────────────────────────────────────

    private static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            List<String> list = com.fasterxml.jackson.databind.ObjectMapper.class
                    .getDeclaredConstructor().newInstance()
                    .readValue(json, new TypeReference<List<String>>() {});
            if (list == null) return Collections.emptyList();
            list.removeIf(s -> s == null || s.isBlank());
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
