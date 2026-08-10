package com.example.app.service.tool.tools;

import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.ModelCapability;
import com.example.app.entity.ModelConfig;
import com.example.app.service.ModelConfigService;
import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图像生成工具
 *
 * 暴露 {@code generateImage} 工具，供 LLM 在 Agent 模式下按需调用文生图能力。
 * 复用 {@link OpenAICompatibleClient#generateImageSync}，从 {@link ModelConfigService}
 * 中查找第一个具备 IMAGE_OUT 能力的已启用模型配置。
 *
 * 返回可插入 Markdown 的图片语法（![Generated Image](url)），供前端 UIImage 组件渲染。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationTool implements ToolComponent {

    private final ModelConfigService modelConfigService;
    private final OpenAICompatibleClient openAICompatibleClient;

    @Tool("根据文本提示生成图片。输入对想要生成图片的详细描述，返回可显示的图片。当用户要求生成、画、绘制图片时调用此工具。")
    String generateImage(String prompt) {
        log.info("[ImageGenerationTool] prompt='{}'", prompt);

        ModelConfig imageModel = modelConfigService.findFirstModelWithCapability(ModelCapability.IMAGE_OUT);
        if (imageModel == null) {
            log.warn("[ImageGenerationTool] No enabled model with IMAGE_OUT capability");
            return "图像生成失败：未配置具备图像生成能力的模型。请在设置中添加图像模型（如 dall-e、sdxl 等）。";
        }

        String modelId = imageModel.getName() + ":" + imageModel.getModelId();
        try {
            String imageUrl = openAICompatibleClient.generateImageSync(
                    imageModel.getModelId(),
                    imageModel.getBaseUrl(),
                    imageModel.getApiKey(),
                    prompt,
                    List.of());
            log.info("[ImageGenerationTool] generated image for model={}, urlLen={}",
                    modelId, imageUrl != null ? imageUrl.length() : 0);
            return "![Generated Image](" + imageUrl + ")";
        } catch (Exception e) {
            log.error("[ImageGenerationTool] failed for model={}: {}", modelId, e.getMessage(), e);
            return "图像生成失败：" + e.getMessage();
        }
    }
}
