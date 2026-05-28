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
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
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