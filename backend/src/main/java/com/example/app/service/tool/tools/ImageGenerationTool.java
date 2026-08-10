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
 * 图像生成工具
 *
 * 暴露 {@code generateImage} 工具，供 LLM 在 Agent 模式下按需调用：
 * 
 * <ul>
 * <li>仅传入 prompt：文生图（txt2img）</li>
 * <li>同时传入 referenceImageUrl：图生图（img2img），基于参考图修改/编辑</li>
 * </ul>
 *
 * 复用 {@link OpenAICompatibleClient#generateImageSync}，从
 * {@link ModelConfigService}
 * 中查找第一个具备 IMAGE_OUT 能力的已启用模型配置。
 *
 * 返回可插入 Markdown 的图片语法（![Generated Image](url)），
 * 由 {@code ToolInvocationStage.collectImageArtifacts} 自动提取到 ctx.artifacts。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationTool implements ToolComponent {

    private final ModelConfigService modelConfigService;
    private final OpenAICompatibleClient openAICompatibleClient;

    @Tool("根据文本提示生成或修改图片。可传入参考图片URL基于原图修改（img2img），也可仅凭文本描述生成新图片（txt2img）。当用户要求生成、画、绘制、修改、编辑图片时调用此工具。")
    String generateImage(
            String prompt,
            @P("可选的参考图片URL，用于基于原图修改或编辑。如用户上传了原图或要求'在这张图上修改'时传入该URL；纯文本生图时不传。") String referenceImageUrl,
            @P("可选的模型ID（格式：服务商:模型名，如 'my-provider:dall-e-3'）。用户指定了特定图像模型时传入；未指定则使用默认配置的首个图像模型。") String requestedModelId) {
        boolean hasRef = referenceImageUrl != null && !referenceImageUrl.isBlank();
        log.info("[ImageGenerationTool] prompt='{}', hasReferenceImage={}, requestedModelId={}", prompt, hasRef, requestedModelId);

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
        List<String> imageUrls = hasRef ? List.of(referenceImageUrl) : List.of();
        try {
            String imageUrl = openAICompatibleClient.generateImageSync(
                    imageModel.getModelId(),
                    imageModel.getBaseUrl(),
                    imageModel.getApiKey(),
                    prompt,
                    imageUrls);
            log.info("[ImageGenerationTool] generated image for model={}, mode={}, urlLen={}",
                    modelId, hasRef ? "img2img" : "txt2img",
                    imageUrl != null ? imageUrl.length() : 0);
            return "![Generated Image](" + imageUrl + ")";
        } catch (Exception e) {
            log.error("[ImageGenerationTool] failed for model={}, mode={}: {}",
                    modelId, hasRef ? "img2img" : "txt2img", e.getMessage(), e);
            return "图像生成失败：" + e.getMessage();
        }
    }
}
