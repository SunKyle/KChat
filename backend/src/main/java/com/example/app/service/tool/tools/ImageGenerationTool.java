package com.example.app.service.tool.tools;

import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.ModelCapability;
import com.example.app.entity.ModelConfig;
import com.example.app.service.ModelConfigService;
import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图像生成工具（文生图，txt2img）
 *
 * <p>暴露 {@code generateImage} 工具，供 LLM 在 Agent 模式下按需调用。
 * 仅支持根据文本提示生成全新图片，不接收参考图。
 *
 * <p>如需基于现有图片修改/编辑，请使用 {@link ImageEditingTool}（img2img）。
 *
 * <p>复用 {@link OpenAICompatibleClient#generateImageSync}，从
 * {@link ModelConfigService}
 * 中查找第一个具备 IMAGE_OUT 能力的已启用模型配置。
 *
 * <p>返回可插入 Markdown 的图片语法（![Generated Image](url)），
 * 由 {@code ToolInvocationStage.collectImageArtifacts} 自动提取到 ctx.artifacts。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationTool implements ToolComponent {

    private final ModelConfigService modelConfigService;
    private final OpenAICompatibleClient openAICompatibleClient;

    @Tool("根据文本提示生成全新的图片（文生图 / txt2img）。当用户要求生成、画、绘制、创建新图片时调用此工具。如需基于现有图片修改或编辑，请改用 editImage 工具。")
    String generateImage(
            String prompt,
            @P("可选的模型ID（格式：服务商:模型名，如 'my-provider:dall-e-3'）。用户指定了特定图像模型时传入；未指定则使用默认配置的首个图像模型。") String requestedModelId) {
        log.info("[ImageGenerationTool] prompt='{}', requestedModelId={}", prompt, requestedModelId);

        ModelConfig imageModel;
        if (requestedModelId != null && !requestedModelId.isBlank()) {
            imageModel = modelConfigService.getConfigWithCapability(requestedModelId, ModelCapability.IMAGE_OUT);
            if (imageModel == null) {
                return "指定的模型不可用或不具备图像生成能力：" + requestedModelId;
            }
        } else {
            imageModel = modelConfigService.findFirstModelWithCapability(ModelCapability.IMAGE_OUT);
        }

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
            log.info("[ImageGenerationTool] generated image for model={}, mode=txt2img, urlLen={}",
                    modelId, imageUrl != null ? imageUrl.length() : 0);
            return "![Generated Image](" + imageUrl + ")";
        } catch (Exception e) {
            log.error("[ImageGenerationTool] failed for model={}, mode=txt2img: {}",
                    modelId, e.getMessage(), e);
            return "图像生成失败：" + e.getMessage();
        }
    }
}
