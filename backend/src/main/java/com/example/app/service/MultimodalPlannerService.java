package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.MultimodalProperties;
import com.example.app.dto.MultimodalPlan;
import com.example.app.dto.MultimodalPlanStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MultimodalPlannerService {

    private static final org.slf4j.Logger promptLog =
            org.slf4j.LoggerFactory.getLogger("PROMPT_LOG");

    private final MultimodalProperties properties;
    private final ModelConfigService modelConfigService;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    private static final int MAX_PLANNER_HISTORY_CHARS = 6000;

    private static final String PLANNER_PROMPT = """
            你是一个多模态任务规划器。根据最近的对话历史、用户输入和图片数量，输出一个 JSON 计划。

            要求：
            - 只输出 JSON，不要输出其他文字
            - steps 是数组，每项包含 type、prompt、text、targetImage
            - type 只能是 vision、image_gen、text 之一
            - vision：需要理解用户上传图片时使用，targetImage 是图片索引（从 0 开始）
            - image_gen：需要生成图片时使用，prompt 是生成图片的描述
            - image_gen 的 prompt 必须是自包含的完整画面描述，不能依赖对话历史，因为它会直接发送给图像生成模型
            - text：需要文本回答时使用
            - 步骤数量不超过 {max_steps}

            示例：
            {"steps":[{"type":"vision","prompt":"分析图片","text":null,"targetImage":0},{"type":"text","prompt":null,"text":"用中文回答","targetImage":null}]}

            最近对话历史：
            {history}

            当前用户输入：{message}
            图片数量：{image_count}
            """;

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
            String prompt = PLANNER_PROMPT
                    .replace("{message}", userMessage == null ? "" : userMessage)
                    .replace("{history}", formatHistory(history))
                    .replace("{image_count}", String.valueOf(imageCount))
                    .replace("{max_steps}", String.valueOf(properties.getMaxSteps()));
            String response = callPlanner(prompt, configuredPlannerModel);
            MultimodalPlan plan = objectMapper.readValue(response, MultimodalPlan.class);
            if (plan != null && plan.steps() != null && !plan.steps().isEmpty()) {
                return plan;
            }
        } catch (Exception e) {
            log.warn("[MultimodalPlanner] LLM 规划失败，使用规则规划: {}", e.getMessage());
        }
        return fallbackPlan(userMessage, imageUrls, history);
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

    private String callPlanner(String prompt, String configuredPlannerModel) {
        String model = configuredPlannerModel != null && !configuredPlannerModel.isBlank()
                ? configuredPlannerModel
                : properties.getPlannerModel();
        if (model == null || model.isBlank()) {
            model = modelConfigService.findDefaultTextModelId();
        }
        if (model != null && !model.isBlank()) {
            promptLog.info("[MultimodalPlanner] 使用规划模型: {}", model);
            var config = modelConfigService.getConfigByModelId(model);
            if (config != null) {
                String actualModelId = model.startsWith(config.getName() + ":")
                        ? model.substring(config.getName().length() + 1)
                        : model;
                return openAICompatibleClient.chatCompletion(
                        actualModelId, config.getBaseUrl(), config.getApiKey(), null, prompt);
            }
            return ollamaClient.generate(List.of(UserMessage.from(prompt)), model);
        }
        promptLog.info("[MultimodalPlanner] 使用默认 Ollama 模型");
        return ollamaClient.generate(List.of(UserMessage.from(prompt)), null);
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
