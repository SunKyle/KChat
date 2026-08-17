package com.example.app.service;

import com.example.app.dto.ChatRequest;
import com.example.app.util.PromptAssembler;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聊天流程编排服务，负责协调聊天过程中的各个环节
 * 
 * <功能说明>
 * - 核心职责：对话生命周期管理、短期记忆管理、消息组装
 * - 设计模式：门面模式（Facade Pattern），为复杂的聊天流程提供统一接口
 * - 依赖关系：依赖 ConversationService、ShortTermMemoryService、PromptAssembler
 * - JPA long_term_memory 已废弃，记忆检索统一由 Cognee 通过 Pipeline 承担
 * 
 * <使用场景>
 * - 同步聊天：ChatService 调用
 * - 流式聊天：StreamingService 调用
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatWorkflowService {

    /**
     * 对话服务，负责对话的创建和管理
     */
    private final ConversationService conversationService;

    /**
     * 短期记忆服务，管理对话上下文
     */
    private final ShortTermMemoryService shortTermMemoryService;

    /**
     * 提示词组装器，将消息组装为 LLM 提示词（已废弃，仅用于旧同步路径）
     */
    private final PromptAssembler promptAssembler;

    /**
     * 获取或创建对话 ID
     * 
     * 如果请求中没有提供对话 ID，则创建新对话；否则使用现有对话。
     * 
     * @param request 聊天请求
     * @return 对话 ID
     */
    public String getOrCreateConversationId(ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation("新对话").getId();
            log.info("[ChatWorkflow] Created new conversation: {}", conversationId);
        } else {
            log.info("[ChatWorkflow] Using existing conversation: {}", conversationId);
        }
        return conversationId;
    }

    /**
     * 获取指定对话的短期记忆（对话历史）
     * 
     * @param conversationId 对话 ID
     * @return 消息列表，按时间顺序排列
     */
    public List<ChatMessage> getShortTermMemory(String conversationId) {
        return shortTermMemoryService.getMemoryContext(conversationId);
    }

    /**
     * 组装消息为 LLM 可理解的格式
     * 
     * 将短期记忆和当前用户消息组合成完整的提示词。
     * 
     * @param shortTermMemory 短期记忆（对话历史）
     * @param userMessage 当前用户消息
     * @return 组装后的消息列表
     */
    public List<ChatMessage> assembleMessages(
            List<ChatMessage> shortTermMemory,
            String userMessage) {
        return promptAssembler.assemble(shortTermMemory, userMessage, null);
    }

    /**
     * 组装消息为 LLM 可理解的格式（带语言偏好）
     * 
     * @param shortTermMemory 短期记忆（对话历史）
     * @param userMessage 当前用户消息
     * @param language 用户语言偏好
     * @return 组装后的消息列表
     */
    public List<ChatMessage> assembleMessages(
            List<ChatMessage> shortTermMemory,
            String userMessage,
            String language) {
        return promptAssembler.assemble(shortTermMemory, userMessage, language);
    }

    public List<ChatMessage> assembleMessages(
            List<ChatMessage> shortTermMemory,
            String userMessage,
            String language,
            String searchContext) {
        return promptAssembler.assemble(shortTermMemory, userMessage, language, searchContext);
    }

    /**
     * 更新短期记忆，添加用户消息和 AI 回复
     * 
     * @param conversationId 对话 ID
     * @param userMessage 用户消息内容
     * @param aiMessage AI 回复内容
     */
    public void updateShortTermMemory(String conversationId, String userMessage, String aiMessage) {
        shortTermMemoryService.updateMemory(conversationId, userMessage, aiMessage);
    }

    /**
     * 更新短期记忆，仅添加用户消息
     * 
     * 用于流式响应场景，先记录用户消息，后续再添加 AI 回复。
     * 
     * @param conversationId 对话 ID
     * @param userMessage 用户消息内容
     */
    public void updateShortTermMemoryWithUserMessage(String conversationId, String userMessage) {
        shortTermMemoryService.updateMemoryWithUserMessage(conversationId, userMessage);
    }

    /**
     * 更新短期记忆，仅添加 AI 消息
     *
     * 用于流式响应场景，流式传输完成后记录完整的 AI 回复。
     *
     * @param conversationId 对话 ID
     * @param aiMessage AI 回复内容
     */
    public void updateShortTermMemoryWithAiMessage(String conversationId, String aiMessage) {
        shortTermMemoryService.updateMemoryWithAiMessage(conversationId, aiMessage);
    }

    /**
     * 清除指定对话的短期记忆缓存
     *
     * 用于消息删除后使缓存失效，确保下次获取时重新加载。
     *
     * @param conversationId 对话 ID
     */
    public void clearShortTermMemory(String conversationId) {
        shortTermMemoryService.clearMemory(conversationId);
    }
}