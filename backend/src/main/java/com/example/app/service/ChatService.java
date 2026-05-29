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

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final OllamaClient ollamaClient;
    private final ChatWorkflowService chatWorkflowService;
    private final MessagePersistenceService messagePersistenceService;
    private final AutoMemoryExtractor autoMemoryExtractor;

    public ChatResponse generateResponse(ChatRequest request) {
        String conversationId = chatWorkflowService.getOrCreateConversationId(request);
        String userMessage = request.getMessage();
        String model = request.getModel();
        String userId = request.getUserId() != null ? request.getUserId() : "default";

        List<ChatMessage> shortTermMemory = chatWorkflowService.getShortTermMemory(conversationId);
        List<MemoryDTO> longTermMemory = chatWorkflowService.recallLongTermMemory(userId, userMessage, 5);
        log.debug("Recalled {} long-term memories for user {}", longTermMemory.size(), userId);

        List<ChatMessage> messages = chatWorkflowService.assembleMessages(shortTermMemory, longTermMemory, userMessage);
        String aiResponse = ollamaClient.generate(messages, model);

        chatWorkflowService.updateShortTermMemory(conversationId, userMessage, aiResponse);
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
