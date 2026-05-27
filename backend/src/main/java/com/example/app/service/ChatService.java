package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.entity.Conversation;
import com.example.app.repository.ConversationRepository;
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
    private final ConversationRepository conversationRepository;

    @Transactional
    public ChatResponse generateResponse(ChatRequest request) {
        String conversationId = request.getConversationId();

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = createNewConversation();
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

    @Transactional
    public String createNewConversation() {
        String conversationId = UUID.randomUUID().toString();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .title("新对话")
                .build();
        conversationRepository.save(conversation);
        return conversationId;
    }
}