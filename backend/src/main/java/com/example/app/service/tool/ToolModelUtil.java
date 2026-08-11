package com.example.app.service.tool;

/**
 * 工具结果中携带模型信息的公共约定
 *
 * <p>使用模型能力的工具（如 analyzeImage、generateImage、editImage）在返回给 LLM
 * 的结果文本前，通过 {@link #wrap(String, String)} 把"实际调用的模型"包进一个
 * 约定标记前缀。{@link ToolExecutor} 执行后用 {@link #unwrap(String)} 统一解析剥离，
 * 将模型信息单独存入 {@link com.example.app.pipeline.context.ConversationContext.ToolResultRecord}，
 * 供 Agent 思考面板（tool_detection / tool_execution 事件）展示，同时保证回填给 LLM
 * 的文本不受标记污染。
 *
 * <p>格式约定：{@code @kc-model:<modelId>:end@\n<实际结果>}。
 * 由于标记紧贴工具自身返回值，不会被 LLM 伪造，故采用简单索引解析即可。
 */
public final class ToolModelUtil {

    private static final String PREFIX = "@kc-model:";
    private static final String SUFFIX = ":end@";

    private ToolModelUtil() {
    }

    /** 把 modelId 包进结果文本；modelId 为空时原样返回，不添加标记。 */
    public static String wrap(String result, String modelId) {
        if (modelId == null || modelId.isBlank() || result == null) {
            return result;
        }
        return PREFIX + modelId + SUFFIX + "\n" + result;
    }

    /** 解析结果文本，剥离模型标记，返回 model 与纯净内容。无标记时 model 为 null。 */
    public static Extracted unwrap(String result) {
        if (result == null) {
            return new Extracted(null, null);
        }
        int start = result.indexOf(PREFIX);
        if (start >= 0) {
            int end = result.indexOf(SUFFIX, start);
            if (end > start) {
                String model = result.substring(start + PREFIX.length(), end);
                String content = result.substring(end + SUFFIX.length());
                if (content.startsWith("\n")) {
                    content = content.substring(1);
                }
                return new Extracted(model, content);
            }
        }
        return new Extracted(null, result);
    }

    /** 解析结果。 */
    public record Extracted(String model, String content) {
    }
}
