package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.MultimodalProperties;
import com.example.app.dto.MultimodalPlan;
import com.example.app.dto.MultimodalPlanStep;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String PLANNER_PROMPT = """
            你是一个多模态任务规划器。根据用户输入和图片数量，输出一个 JSON 计划。

            要求：
            - 只输出 JSON，不要输出其他文字
            - steps 是数组，每项包含 type、prompt、text、targetImage
            - type 只能是 vision、image_gen、text 之一
            - vision：需要理解用户上传图片时使用，targetImage 是图片索引（从 0 开始）
            - image_gen：需要生成图片时使用，prompt 是生成图片的描述
            - text：需要文本回答时使用
            - 步骤数量不超过 {max_steps}

            示例：
            {"steps":[{"type":"vision","prompt":"分析图片","text":null,"targetImage":0},{"type":"text","prompt":null,"text":"用中文回答","targetImage":null}]}

            用户输入：{message}
            图片数量：{image_count}
            """;

    public MultimodalPlan plan(String userMessage, List<String> imageUrls) {
        int imageCount = imageUrls == null ? 0 : imageUrls.size();
        try {
            String prompt = PLANNER_PROMPT
                    .replace("{message}", userMessage == null ? "" : userMessage)
                    .replace("{image_count}", String.valueOf(imageCount))
                    .replace("{max_steps}", String.valueOf(properties.getMaxSteps()));
            String response = callPlanner(prompt);
            MultimodalPlan plan = objectMapper.readValue(response, MultimodalPlan.class);
            if (plan != null && plan.steps() != null && !plan.steps().isEmpty()) {
                return plan;
            }
        } catch (Exception e) {
            log.warn("[MultimodalPlanner] LLM 规划失败，使用规则规划: {}", e.getMessage());
        }
        return fallbackPlan(userMessage, imageUrls);
    }

    private String callPlanner(String prompt) {
        String model = properties.getPlannerModel();
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

    private MultimodalPlan fallbackPlan(String userMessage, List<String> imageUrls) {
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
            steps.add(new MultimodalPlanStep("image_gen", text, null, imageCount > 0 ? 0 : null));
        }

        if (steps.isEmpty() || (!wantsImage && imageCount == 0)) {
            steps.add(new MultimodalPlanStep("text", null, text, null));
        }

        int maxSteps = Math.max(1, properties.getMaxSteps());
        return new MultimodalPlan(steps.subList(0, Math.min(steps.size(), maxSteps)));
    }
}
