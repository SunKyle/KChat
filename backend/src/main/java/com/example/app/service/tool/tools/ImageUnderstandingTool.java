package com.example.app.service.tool.tools;

import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.ModelCapability;
import com.example.app.entity.ModelConfig;
import com.example.app.service.ModelConfigService;
import com.example.app.service.UserSettingService;
import com.example.app.service.ai.AiServiceFactory;
import com.example.app.service.tool.ToolComponent;
import com.example.app.service.tool.ToolModelUtil;
import com.example.app.service.tool.UserContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图片理解工具
 *
 * <p>暴露 {@code analyzeImage} 工具，供 LLM 在 Agent 模式下按需调用。当主模型不具备
 * 视觉能力（IMAGE_IN）时，可通过此工具委托给具备视觉能力的模型识别图片内容，
 * 返回文本描述供主模型生成回复。
 *
 * <p>设计动机：让纯文本主模型（如 DeepSeek、Qwen 文本版）也能处理用户上传的图片，
 * 无需切换模型，通过 function calling 间接获得视觉能力。
 *
 * <p>调用链：
 * <ol>
 * <li>LLM 调用 {@code analyzeImage(imageUrl, question, requestedModelId)}</li>
 * <li>工具从 {@link ModelConfigService} 查找具备 IMAGE_IN 能力的模型
 *     （用户指定 → 校验能力；未指定 → 首个具备能力的模型）</li>
 * <li>通过 {@link OpenAICompatibleClient#attachImagesToLastUserMessage} 把 imageUrl
 *     转为 ImageContent 附加到 UserMessage</li>
 * <li>通过 {@link AiServiceFactory#getChatModel} 获取视觉模型的 ChatModel，
 *     调用 {@code chat(ChatRequest)} 获取文本描述</li>
 * <li>返回文本描述给主模型，主模型基于描述回复用户</li>
 * </ol>
 *
 * <p>与 {@link ImageGenerationTool} 对称：
 * <ul>
 * <li>ImageGenerationTool：TEXT_IN → IMAGE_OUT（文字生成图片）</li>
 * <li>ImageUnderstandingTool：IMAGE_IN → TEXT_OUT（图片生成文字）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageUnderstandingTool implements ToolComponent {

    private final ModelConfigService modelConfigService;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final AiServiceFactory aiServiceFactory;
    private final UserSettingService userSettingService;

    @Override
    public String requiredCapability() {
        return ModelCapability.IMAGE_IN;
    }

    @Tool("识别和理解图片内容。当用户上传图片并询问图片内容、要求描述图片、识别图中文字/物体/场景/风格时调用此工具。工具会调用支持视觉的模型分析图片并返回详细的文本描述。")
    String analyzeImage(
            String imageUrl,
            @P("针对图片的具体问题或关注点，如'图中有什么文字'、'描述图片风格'、'图中有几个人'。未指定或为空时返回图片的完整内容描述。") String question,
            @P("可选的视觉模型ID（格式：服务商:模型名，如 'my-provider:gpt-4o' 或 Ollama 的 'llava'）。用户指定了特定视觉模型时传入；未指定则使用默认配置的首个支持视觉的模型。") String requestedModelId) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return "图片识别失败：未提供图片URL。";
        }

        log.info("[ImageUnderstandingTool] imageUrl='{}', question='{}', requestedModelId={}",
                imageUrl, question, requestedModelId);

        // 1. 解析视觉模型：LLM 显式指定 > 工具箱配置的默认模型 > 自动选择
        String visionModelId;
        String requested = requestedModelId;
        if (requested == null || requested.isBlank()) {
            requested = userSettingService.getToolModel(UserContextHolder.get(), "analyzeImage");
        }
        if (requested != null && !requested.isBlank()) {
            ModelConfig specified = modelConfigService.getConfigWithCapability(
                    requested, ModelCapability.IMAGE_IN);
            if (specified == null) {
                log.warn("[ImageUnderstandingTool] requested model '{}' not available / lacks IMAGE_IN, "
                        + "falling back to auto-select", requested);
                specified = modelConfigService.findFirstModelWithCapability(ModelCapability.IMAGE_IN);
            }
            if (specified == null) {
                log.warn("[ImageUnderstandingTool] No enabled model with IMAGE_IN capability");
                return "图片识别失败：未配置具备视觉能力的模型。请在设置中添加视觉模型"
                        + "（如 gpt-4o、llava、qwen2.5-vl 等）并确保其 capabilities 包含 IMAGE_IN。";
            }
            visionModelId = specified.getName() + ":" + specified.getModelId();
        } else {
            ModelConfig first = modelConfigService.findFirstModelWithCapability(
                    ModelCapability.IMAGE_IN);
            if (first == null) {
                log.warn("[ImageUnderstandingTool] No enabled model with IMAGE_IN capability");
                return "图片识别失败：未配置具备视觉能力的模型。请在设置中添加视觉模型"
                        + "（如 gpt-4o、llava、qwen2.5-vl 等）并确保其 capabilities 包含 IMAGE_IN。";
            }
            visionModelId = first.getName() + ":" + first.getModelId();
        }

        // 2. 构造请求消息：question 作为文本，imageUrl 转 ImageContent 附加
        String promptText = (question == null || question.isBlank())
                ? "请详细描述这张图片的内容，包括主体、场景、文字、风格等关键信息。"
                : question;
        List<ChatMessage> messages = List.of(UserMessage.from(promptText));
        List<String> imageUrls = List.of(imageUrl);

        try {
            List<ChatMessage> messagesWithImage = openAICompatibleClient
                    .attachImagesToLastUserMessage(messages, imageUrls);
            if (messagesWithImage == messages) {
                // attachImagesToLastUserMessage 原样返回说明图片附加失败（如 URL 不可达）
                return "图片识别失败：无法获取图片内容，URL 可能无效或服务不可达：" + imageUrl;
            }

            // 3. 调用视觉模型
            ChatModel chatModel = aiServiceFactory.getChatModel(visionModelId);
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messagesWithImage)
                    .build();
            ChatResponse response = chatModel.chat(chatRequest);
            String description = response.aiMessage().text();

            if (description == null || description.isBlank()) {
                log.warn("[ImageUnderstandingTool] Vision model {} returned empty description", visionModelId);
                return "图片识别失败：视觉模型未返回有效内容。";
            }

            log.info("[ImageUnderstandingTool] vision model={}, question='{}', description length={}",
                    visionModelId, promptText, description.length());
            return ToolModelUtil.wrap(description, visionModelId);
        } catch (Exception e) {
            log.error("[ImageUnderstandingTool] failed for vision model={}, imageUrl={}: {}",
                    visionModelId, imageUrl, e.getMessage(), e);
            return "图片识别失败：" + e.getMessage();
        }
    }
}
