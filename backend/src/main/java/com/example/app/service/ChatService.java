package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.dto.MemoryDTO;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 聊天服务类，负责处理同步聊天请求的完整流程
 * 
 * <功能说明>
 * - 核心职责：协调对话管理、记忆检索、LLM调用、消息持久化等环节
 * - 设计模式：编排器模式（Orchestrator Pattern），将多个服务组合成完整流程
 * - 依赖关系：依赖 OllamaClient、ChatWorkflowService、MessagePersistenceService、AutoMemoryExtractor
 * 
 * <使用场景>
 * - 同步消息响应：用户发送消息后等待完整回复
 * - 记忆增强对话：自动融合短期和长期记忆
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    /**
     * Ollama 客户端，用于调用本地 LLM 模型
     */
    private final OllamaClient ollamaClient;

    /**
     * 聊天流程编排服务，负责记忆管理和消息组装
     */
    private final ChatWorkflowService chatWorkflowService;

    /**
     * 消息持久化服务，负责将消息保存到数据库
     */
    private final MessagePersistenceService messagePersistenceService;

    /**
     * 自动记忆提取器，对话完成后自动提取值得保存的信息
     */
    private final AutoMemoryExtractor autoMemoryExtractor;

    /**
     * 用户配置服务，用于获取语言偏好
     */
    private final UserProfileService userProfileService;

    /**
     * 处理用户聊天请求，生成 AI 响应
     * 
     * 执行流程：
     * 1. 获取或创建对话 ID
     * 2. 检索短期记忆（对话历史）
     * 3. 检索长期记忆（基于语义相似度）
     * 4. 组装消息为 LLM 格式
     * 5. 调用 LLM 生成响应
     * 6. 更新短期记忆
     * 7. 持久化消息
     * 8. 尝试提取新记忆
     * 
     * @param request 聊天请求，包含消息内容、模型选择、用户ID等
     * @return 聊天响应，包含 AI 回复内容和消息元数据
     */
    public ChatResponse generateResponse(ChatRequest request) {
        // 1. 获取或创建对话 ID
        String conversationId = chatWorkflowService.getOrCreateConversationId(request);
        String userMessage = request.getMessage();
        String model = request.getModel();
        String userId = request.getUserId() != null ? request.getUserId() : "default";

        // 2. 检索短期记忆（对话历史）
        List<ChatMessage> shortTermMemory = chatWorkflowService.getShortTermMemory(conversationId);
        
        // 3. 检索长期记忆（基于语义相似度召回）
        List<MemoryDTO> longTermMemory = chatWorkflowService.recallLongTermMemory(userId, userMessage, 5);
        log.debug("Recalled {} long-term memories for user {}", longTermMemory.size(), userId);

        // 4. 查询用户语言偏好，组装消息为 LLM 可理解的格式
        String language = userProfileService.getLanguage(userId);
        List<ChatMessage> messages = chatWorkflowService.assembleMessages(shortTermMemory, longTermMemory, userMessage, language);
        
        // 5. 调用 LLM 生成响应
        String aiResponse = ollamaClient.generate(messages, model);

        // 6. 更新短期记忆
        chatWorkflowService.updateShortTermMemory(conversationId, userMessage, aiResponse);
        
        // 7. 持久化消息到数据库
        messagePersistenceService.saveMessages(conversationId, userMessage, aiResponse, request.getImageUrls());

        // 8. 异步尝试从对话中提取新记忆
        autoMemoryExtractor.tryExtract(conversationId, userId);

        // 构建响应
        return ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content(aiResponse)
                .role("assistant")
                .conversationId(conversationId)
                .build();
    }
}
