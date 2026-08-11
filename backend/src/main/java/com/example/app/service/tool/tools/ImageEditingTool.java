package com.example.app.service.tool.tools;

import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.ModelCapability;
import com.example.app.entity.ModelConfig;
import com.example.app.service.ModelConfigService;
import com.example.app.service.UserSettingService;
import com.example.app.service.tool.ToolComponent;
import com.example.app.service.tool.ToolModelUtil;
import com.example.app.service.tool.UserContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图像编辑工具（图生图，img2img）
 *
 * <p>暴露 {@code editImage} 工具，供 LLM 在 Agent 模式下按需调用。
 * 基于参考图片（referenceImageUrl）和文本提示（prompt）修改或编辑现有图片。
 *
 * <p>典型场景：
 * <ul>
 * <li>用户上传原图并要求"在这张图上修改..."</li>
 * <li>用户要求对已有图片做风格转换、局部修改、添加/删除元素等</li>
 * </ul>
 *
 * <p>与 {@link ImageGenerationTool} 对称：
 * <ul>
 * <li>ImageGenerationTool：纯文本 → 新图片（txt2img，无参考图）</li>
 * <li>ImageEditingTool：参考图 + 文本 → 修改后图片（img2img，必须有原图）</li>
 * </ul>
 *
 * <p>复用 {@link OpenAICompatibleClient#generateImageSync}，
 * 从 {@link ModelConfigService} 中查找具备 IMAGE_OUT 能力的已启用模型配置。
 *
 * <p>返回可插入 Markdown 的图片语法（![Edited Image](url)），
 * 由 {@code ToolInvocationStage.collectImageArtifacts} 自动提取到 ctx.artifacts。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageEditingTool implements ToolComponent {

    private final ModelConfigService modelConfigService;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final UserSettingService userSettingService;

    @Override
    public String requiredCapability() {
        return ModelCapability.IMAGE_OUT;
    }

    @Tool("基于参考图片进行修改或编辑（图生图 / img2img）。当用户上传了原图并要求修改、编辑、调整、变换图片时调用此工具。如需从零生成新图片，请改用 generateImage 工具。")
    String editImage(
            String prompt,
            String referenceImageUrl,
            @P("可选的模型ID（格式：服务商:模型名，如 'my-provider:dall-e-3'）。用户指定了特定图像模型时传入；未指定则使用默认配置的首个图像模型。") String requestedModelId) {

        if (referenceImageUrl == null || referenceImageUrl.isBlank()) {
            return "图像编辑失败：未提供参考图片URL。editImage 工具必须传入 referenceImageUrl 参数。"
                    + "如用户未上传原图，请改用 generateImage 工具从零生成新图片。";
        }

        log.info("[ImageEditingTool] prompt='{}', referenceImageUrl='{}', requestedModelId={}",
                prompt, referenceImageUrl, requestedModelId);

        // LLM 显式指定 > 工具箱配置的默认模型 > 自动选择
        String requested = requestedModelId;
        if (requested == null || requested.isBlank()) {
            requested = userSettingService.getToolModel(UserContextHolder.get(), "editImage");
        }
        ModelConfig imageModel;
        if (requested != null && !requested.isBlank()) {
            imageModel = modelConfigService.getConfigWithCapability(requested, ModelCapability.IMAGE_OUT);
            if (imageModel == null) {
                // 指定了无效/不可用的模型：回退到自动选择，避免一次失败中断整个编辑任务
                log.warn("[ImageEditingTool] requested model '{}' not available / lacks IMAGE_OUT, "
                        + "falling back to auto-select", requested);
                imageModel = modelConfigService.findFirstModelWithCapability(ModelCapability.IMAGE_OUT);
            }
        } else {
            imageModel = modelConfigService.findFirstModelWithCapability(ModelCapability.IMAGE_OUT);
        }

        if (imageModel == null) {
            log.warn("[ImageEditingTool] No enabled model with IMAGE_OUT capability");
            return "图像编辑失败：未配置具备图像生成能力的模型。请在设置中添加图像模型（如 dall-e、sdxl 等）。";
        }

        String modelId = imageModel.getName() + ":" + imageModel.getModelId();
        List<String> imageUrls = List.of(referenceImageUrl);
        try {
            String imageUrl = openAICompatibleClient.generateImageSync(
                    imageModel.getModelId(),
                    imageModel.getBaseUrl(),
                    imageModel.getApiKey(),
                    prompt,
                    imageUrls);
            log.info("[ImageEditingTool] edited image for model={}, mode=img2img, urlLen={}",
                    modelId, imageUrl != null ? imageUrl.length() : 0);
            return ToolModelUtil.wrap("![Edited Image](" + imageUrl + ")", modelId);
        } catch (Exception e) {
            log.error("[ImageEditingTool] failed for model={}, mode=img2img: {}",
                    modelId, e.getMessage(), e);
            return "图像编辑失败：" + e.getMessage();
        }
    }
}
