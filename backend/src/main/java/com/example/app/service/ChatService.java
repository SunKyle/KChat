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
     * 核心对话生成链路。
     * 流程：召回长期记忆 $\rightarrow$ 组装上下文 $\rightarrow$ LLM 生成 $\rightarrow$ 更新短期记忆 $\rightarrow$ 异步提取知识点。
     * <p>
     * 注意：此处去掉了类级别的 @Transactional，避免 LLM 网络调用期间长时间占用数据库连接池导致系统崩溃。
     */
    public ChatResponse generateResponse(ChatRequest request) {
        String conversationId = request.getConversationId();

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation("新对话").getId();
        }

        String userMessage = request.getMessage();
        String model = request.getModel();
        String userId = request.getUserId() != null ? request.getUserId() : "default";

        // 1. 召回短期记忆（当前对话上下文窗口）
        List<ChatMessage> shortTermMemory = memoryService.getMemoryContext(conversationId);

        // 2. 语义召回长期记忆（基于当前 Query 在 Redis Vector Store 中检索相关事实）
        List<MemoryDTO> longTermMemory = memoryService.recallLongTermMemory(userId, userMessage, 5);
        log.debug("Recalled {} long-term memories for user {}", longTermMemory.size(), userId);

        // 3. Prompt 组装：将 [长期记忆 + 短期记忆 + 当前输入] 转换为 LLM 可理解的消息序列
        List<ChatMessage> messages = promptAssembler.assemble(shortTermMemory, longTermMemory, userMessage);

        // 4. LLM 生成（阻塞 IO 操作）
        String aiResponse = ollamaClient.generate(messages, model);

        // 5. 记忆同步与持久化
        memoryService.updateMemory(conversationId, userMessage, aiResponse);
        messagePersistenceService.saveMessages(conversationId, userMessage, aiResponse, request.getImageUrls());

        // 6. 触发异步记忆提取：分析对话内容并将其转化为持久化知识点（由 AutoMemoryExtractor 内部控制阈值）
        autoMemoryExtractor.tryExtract(conversationId, userId);

        return ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content(aiResponse)
                .role("assistant")
                .conversationId(conversationId)
                .build();
    }
}
