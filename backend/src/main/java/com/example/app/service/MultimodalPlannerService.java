package com.example.app.service;

import com.example.app.config.MultimodalProperties;
import com.example.app.dto.MultimodalPlan;
import com.example.app.dto.MultimodalPlanStep;
import com.example.app.service.ai.AiServiceFactory;
import com.example.app.service.ai.MultimodalPlannerAI;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 多模态任务规划服务
 *
 * LLM 调用与结构化输出由 LangChain4j {@link AiServiceFactory} +
 * {@link MultimodalPlannerAI} 统一处理，框架自动注入 JSON Schema 并反序列化为
 * {@link MultimodalPlan}，替代原先手写的 {@code objectMapper.readValue} 解析。
 * 规则降级（{@link #fallbackPlan}）与历史格式化保持自实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultimodalPlannerService {

    private static final org.slf4j.Logger promptLog =
            org.slf4j.LoggerFactory.getLogger("PROMPT_LOG");

    private final MultimodalProperties properties;
    private final ModelConfigService modelConfigService;
    private final AiServiceFactory aiServiceFactory;

    private static final int MAX_PLANNER_HISTORY_CHARS = 6000;

    public MultimodalPlan plan(String userMessage, List<String> imageUrls) {
        return plan(userMessage, imageUrls, null, null);
    }

    public MultimodalPlan plan(String userMessage, List<String> imageUrls, String configuredPlannerModel) {
        return plan(userMessage, imageUrls, configuredPlannerModel, null);
    }

    public MultimodalPlan plan(String userMessage, List<String> imageUrls,
            String configuredPlannerModel, List<ChatMessage> history) {
        int imageCount = imageUrls == null ? 0 : imageUrls.size();
        try {
            String prompt = buildPlannerPrompt(userMessage, imageCount, history);
            String model = resolvePlannerModel(configuredPlannerModel);
            promptLog.info("[MultimodalPlanner] 使用规划模型: {}", model);
            MultimodalPlannerAI planner = aiServiceFactory.create(MultimodalPlannerAI.class, model);
            MultimodalPlan plan = planner.plan(prompt);
            if (plan != null && plan.steps() != null && !plan.steps().isEmpty()) {
                return plan;
            }
        } catch (Exception e) {
            log.warn("[MultimodalPlanner] LLM 规划失败，使用规则规划: {}", e.getMessage());
        }
        return fallbackPlan(userMessage, imageUrls, history);
    }

    /**
     * 拼装传给 {@link MultimodalPlannerAI#plan(String)} 的 UserMessage 文本，
     * 包含对话历史、当前输入、图片数量、最大步数等动态上下文。
     */
    private String buildPlannerPrompt(String userMessage, int imageCount, List<ChatMessage> history) {
        return """
                最近对话历史：
                %s

                当前用户输入：%s
                图片数量：%d
                步骤数量不超过 %d
                """.formatted(
                formatHistory(history),
                userMessage == null ? "" : userMessage,
                imageCount,
                properties.getMaxSteps());
    }

    /**
     * 解析规划模型标识：优先用调用方传入的 configuredPlannerModel，
     * 其次用配置文件中的 multimodal.plannerModel，最后回退到默认文本模型。
     */
    private String resolvePlannerModel(String configuredPlannerModel) {
        String model = configuredPlannerModel != null && !configuredPlannerModel.isBlank()
                ? configuredPlannerModel
                : properties.getPlannerModel();
        if (model == null || model.isBlank()) {
            model = modelConfigService.findDefaultTextModelId();
        }
        return model;
    }

    private String formatHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "（无）";
        }

        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            String role;
            if (msg instanceof SystemMessage) {
                role = "系统";
            } else if (msg instanceof AiMessage) {
                role = "助手";
            } else {
                role = "用户";
            }
            String text = msg.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            sb.append(role).append(": ").append(text.replace("\n", " ")).append("\n");
        }

        String formatted = sb.toString().trim();
        if (formatted.length() > MAX_PLANNER_HISTORY_CHARS) {
            formatted = "…（历史过长已截断）\n" + formatted.substring(formatted.length() - MAX_PLANNER_HISTORY_CHARS);
        }
        return formatted.isEmpty() ? "（无）" : formatted;
    }

    private MultimodalPlan fallbackPlan(String userMessage, List<String> imageUrls,
            List<ChatMessage> history) {
        List<MultimodalPlanStep> steps = new ArrayList<>();
        String text = userMessage == null ? "" : userMessage.trim();
        int imageCount = imageUrls == null ? 0 : imageUrls.size();

        if (imageCount > 0) {
            steps.add(new MultimodalPlanStep("vision", text, null, 0));
        }

        String lower = text.toLowerCase();
        boolean wantsImage = lower.contains("生成图片")
                || lower.contains("画一个")
                || lower.contains("画一张")
                || lower.contains("draw")
                || (lower.contains("生成") && lower.contains("图"));
        if (wantsImage) {
            steps.add(new MultimodalPlanStep(
                    "image_gen", buildFallbackImagePrompt(text, history), null,
                    imageCount > 0 ? 0 : null));
        }

        if (steps.isEmpty() || (!wantsImage && imageCount == 0)) {
            steps.add(new MultimodalPlanStep("text", null, text, null));
        }

        int maxSteps = Math.max(1, properties.getMaxSteps());
        return new MultimodalPlan(steps.subList(0, Math.min(steps.size(), maxSteps)));
    }

    private String buildFallbackImagePrompt(String userMessage, List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return userMessage;
        }

        List<ChatMessage> recent = history.size() > 6
                ? history.subList(history.size() - 6, history.size())
                : history;
        StringBuilder sb = new StringBuilder("结合以下对话上下文生成图片：\n");
        for (ChatMessage msg : recent) {
            String role = msg instanceof AiMessage ? "助手" : "用户";
            String content = msg.text();
            if (content == null || content.isBlank()) {
                continue;
            }
            sb.append(role).append(": ").append(content.replace("\n", " ")).append("\n");
        }
        sb.append("用户当前需求：").append(userMessage);

        String prompt = sb.toString().trim();
        if (prompt.length() > 3000) {
            return "…（上下文过长已截断）\n" + prompt.substring(prompt.length() - 3000);
        }
        return prompt;
    }
}
