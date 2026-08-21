package com.example.app.service;

import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.entity.Message;
import com.example.app.pipeline.PipelineEntryDispatcher;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 聊天服务类，负责处理同步聊天请求的完整流程
 *
 * <p>核心职责：编排 Pipeline 执行 + 处理重新生成请求。
 * Pipeline 负责记忆召回 / Web 搜索 / Skill 解析 / LLM 调用 / 消息持久化等所有阶段。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    /**
     * 聊天流程编排服务，用于对话创建和短期记忆清理
     */
    private final ChatWorkflowService chatWorkflowService;

    /**
     * 消息数据访问层，重新生成时查询 / 校验原消息
     */
    private final MessageRepository messageRepository;

    /**
     * 事务模板，用于手动控制事务边界
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * Pipeline 入口调度器 — 统一封装 Agent / Simple Chat 入口分支
     */
    private final PipelineEntryDispatcher pipelineEntryDispatcher;

    /**
     * JSON 序列化 / 反序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * 处理用户聊天请求，生成 AI 响应。
     *
     * <p>执行流程已迁移到 Context Pipeline：
     * 预处理 → 组装 → 模型路由 → 后处理，全部由 {@link PipelineEntryDispatcher} 驱动。
     */
    public ChatResponse generateResponse(ChatRequest request) {
        String conversationId = chatWorkflowService.getOrCreateConversationId(request);

        ConversationContext ctx = ConversationContext.fromRequest(request);
        ctx.setConversationId(conversationId);
        ctx.setAgentMode(request.isAgentMode());
        // 入口调度统一委托给 PipelineEntryDispatcher，避免与 StreamingService 重复
        if (ctx.isAgentMode()) {
            pipelineEntryDispatcher.executeAgentChat(ctx);
        } else {
            pipelineEntryDispatcher.executeSimpleChat(ctx);
        }

        return ChatResponse.builder()
                .messageId(ctx.getAiMessageId())
                .content(ctx.getLlmResponse())
                .role("assistant")
                .conversationId(ctx.getConversationId())
                .title(ctx.getGeneratedTitle())
                .images(ctx.getArtifacts() != null
                        ? ctx.getArtifacts().stream()
                                .filter(a -> "image".equals(a.type()))
                                .map(a -> a.url())
                                .toList()
                        : null)
                .artifacts(ctx.getArtifacts())
                .build();
    }

    /**
     * 重新生成指定消息的响应。
     *
     * <p>复用 Pipeline 流程：预处理（记忆召回 / Web 搜索 / Skill 解析）→
     * 组装 → 模型路由 → 后处理（记忆更新 / 消息持久化 / 记忆提取 / 标题生成）。
     *
     * <p>消息持久化策略：通过 {@code ctx.setAiMessageId(messageId)} 复用原 AI 消息 ID，
     * {@link com.example.app.pipeline.stage.postprocess.MessagePersistenceStage} 调用
     * {@code messageRepository.save(...)} 时 JPA 走 upsert 语义覆盖原行，
     * 从而保留 messageId、避免消息堆积；用户消息则因 sync 路径下
     * {@code MessagePrePersistenceStage} 不触发而保持原样。
     *
     * @param conversationId 对话ID
     * @param messageId      要重新生成的消息ID（必须是AI消息）
     * @param model          模型名称
     * @param userId         用户ID
     * @return 重新生成的响应
     */
    public ChatResponse regenerateResponse(String conversationId, String messageId, String model, String userId) {
        log.info("[CHAT] Regenerating response for message {} in conversation {}", messageId, conversationId);

        Message targetMessage = messageRepository.findById(messageId).orElse(null);
        if (targetMessage == null) {
            throw new IllegalArgumentException("消息不存在: " + messageId);
        }

        if (!targetMessage.getConversationId().equals(conversationId)) {
            throw new IllegalArgumentException(
                    "消息不属于指定对话: messageId=" + messageId + ", conversationId=" + conversationId);
        }

        if (!"assistant".equals(targetMessage.getRole())) {
            throw new IllegalArgumentException("只能重新生成AI消息，当前消息角色: " + targetMessage.getRole());
        }

        List<Message> messagesBefore = messageRepository.findByConversationIdAndTimestampLessThanOrderByTimestampAsc(
                conversationId, targetMessage.getTimestamp());

        Message precedingUserMessage = null;
        for (int i = messagesBefore.size() - 1; i >= 0; i--) {
            if ("user".equals(messagesBefore.get(i).getRole())) {
                precedingUserMessage = messagesBefore.get(i);
                break;
            }
        }

        if (precedingUserMessage == null) {
            throw new IllegalArgumentException("未找到对应的用户消息");
        }

        // 删除目标消息之后的所有消息（目标消息本身保留，由 Pipeline 通过 ID 覆盖更新）
        transactionTemplate.executeWithoutResult(status -> {
            messageRepository.deleteByConversationIdAndTimestampGreaterThan(
                    conversationId, targetMessage.getTimestamp());
            log.info("[CHAT] Deleted messages after {} in conversation {}", messageId, conversationId);
        });

        // 清除短期记忆缓存，让 Pipeline 内的 ShortTermMemoryStage 从 DB 回退加载完整历史
        chatWorkflowService.clearShortTermMemory(conversationId);

        // 构建 ChatRequest，复用原用户消息内容、图片、模型、用户 ID、对话 ID
        ChatRequest pipelineRequest = ChatRequest.builder()
                .conversationId(conversationId)
                .message(precedingUserMessage.getContent())
                .imageUrls(parseImageUrls(precedingUserMessage.getImages()))
                .model(model)
                .userId(userId)
                .build();

        ConversationContext ctx = ConversationContext.fromRequest(pipelineRequest);
        ctx.setConversationId(conversationId);
        // 复用原 AI 消息 ID，MessagePersistenceStage 会通过 JPA save 的 upsert 语义覆盖原行
        ctx.setAiMessageId(messageId);
        // 重新生成默认走 SIMPLE_CHAT，保持与原实现一致的行为
        pipelineEntryDispatcher.executeSimpleChat(ctx);

        return ChatResponse.builder()
                .messageId(ctx.getAiMessageId())
                .content(ctx.getLlmResponse())
                .role("assistant")
                .conversationId(ctx.getConversationId())
                .title(ctx.getGeneratedTitle())
                .images(ctx.getArtifacts() != null
                        ? ctx.getArtifacts().stream()
                                .filter(a -> "image".equals(a.type()))
                                .map(a -> a.url())
                                .toList()
                        : null)
                .artifacts(ctx.getArtifacts())
                .build();
    }

    private List<String> parseImageUrls(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse image URLs from JSON: {}", imagesJson, e);
            return List.of();
        }
    }
}
