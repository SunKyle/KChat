package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.dto.MemoryDTO;
import com.example.app.util.PromptAssembler;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 对话服务
 *
 * 核心对话流程编排：记忆召回 -> Prompt 组装 -> LLM 生成 -> 记忆持久化
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final OllamaClient ollamaClient;
    private final MemoryService memoryService;
    private final MessagePersistenceService messagePersistenceService;
    private final ConversationService conversationService;
    private final PromptAssembler promptAssembler;
    private final AutoMemoryExtractor autoMemoryExtractor;

    /**
     * 生成对话回复
     *
     * 核心流程：
     * 1. 创建/获取对话：无 conversationId 时创建新对话
     * 2. 召回短期记忆：获取当前对话上下文窗口
     * 3. 语义召回长期记忆：基于用户输入检索相关事实
     * 4. 组装 Prompt：将 [长期记忆 + 短期记忆 + 当前输入] 转换为消息序列
     * 5. LLM 生成：调用 Ollama 生成响应
     * 6. 记忆持久化：更新短期记忆 + 保存消息到数据库
     * 7. 异步提取记忆：触发长期记忆提取
     *
     * 设计决策：
     * - 无 @Transactional：避免 LLM 网络调用期间长时间占用数据库连接池
     * - 记忆提取异步执行：不阻塞用户响应
     *
     * @param request 对话请求
     * @return 对话响应
     */
    public ChatResponse generateResponse(ChatRequest request) {
        String conversationId = request.getConversationId();

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation("新对话").getId();
        }

        String userMessage = request.getMessage();
        String model = request.getModel();
        String userId = request.getUserId() != null ? request.getUserId() : "default";

        List<ChatMessage> shortTermMemory = memoryService.getMemoryContext(conversationId);
        List<MemoryDTO> longTermMemory = memoryService.recallLongTermMemory(userId, userMessage, 5);
        log.debug("Recalled {} long-term memories for user {}", longTermMemory.size(), userId);
        List<ChatMessage> messages = promptAssembler.assemble(shortTermMemory, longTermMemory, userMessage);
        String aiResponse = ollamaClient.generate(messages, model);

        memoryService.updateMemory(conversationId, userMessage, aiResponse);
        messagePersistenceService.saveMessages(conversationId, userMessage, aiResponse, request.getImageUrls());

        autoMemoryExtractor.tryExtract(conversationId, userId);

        return ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content(aiResponse)
                .role("assistant")
                .conversationId(conversationId)
                .build();
    }
}
