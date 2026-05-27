package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    @Transactional
    public ChatResponse generateResponse(ChatRequest request) {
        String conversationId = request.getConversationId();

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation("新对话").getId();
        }

        String userMessage = request.getMessage();
        String model = request.getModel();

        List<ChatMessage> context = memoryService.getMemoryContext(conversationId);

        List<ChatMessage> messages = new ArrayList<>(context);
        messages.add(UserMessage.from(userMessage));

        String aiResponse = ollamaClient.generate(messages, model);

        memoryService.updateMemory(conversationId, userMessage, aiResponse);

        messagePersistenceService.saveMessages(conversationId, userMessage, aiResponse, request.getImageUrls());

        return ChatResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .content(aiResponse)
                .role("assistant")
                .conversationId(conversationId)
                .build();
    }
}