package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.entity.Conversation;
import com.example.app.entity.Message;
import com.example.app.repository.ConversationRepository;
import com.example.app.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final OllamaClient ollamaClient;
    private final MemoryService memoryService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        saveMessages(conversationId, userMessage, aiResponse, request.getImageUrls());

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

    @Transactional
    public void saveMessages(String conversationId, String userMessage, String aiResponse, List<String> imageUrls) {
        String imagesJson = null;
        if (imageUrls != null && !imageUrls.isEmpty()) {
            try {
                imagesJson = objectMapper.writeValueAsString(imageUrls);
            } catch (JsonProcessingException e) {
                imagesJson = null;
            }
        }

        Message userMsg = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .content(userMessage)
                .role("user")
                .images(imagesJson)
                .build();

        Message aiMsg = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .content(aiResponse)
                .role("assistant")
                .build();

        messageRepository.save(userMsg);
        messageRepository.save(aiMsg);

        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversationRepository.save(conversation);
        });
    }

    @Transactional
    public void saveUserMessage(String conversationId, String content, List<String> imageUrls) {
        String imagesJson = null;
        if (imageUrls != null && !imageUrls.isEmpty()) {
            try {
                imagesJson = objectMapper.writeValueAsString(imageUrls);
            } catch (JsonProcessingException e) {
                imagesJson = null;
            }
        }

        Message userMsg = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .content(content)
                .role("user")
                .images(imagesJson)
                .build();

        messageRepository.save(userMsg);
    }

    @Transactional
    public void saveAssistantMessage(String conversationId, String content) {
        Message aiMsg = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .content(content)
                .role("assistant")
                .build();

        messageRepository.save(aiMsg);
    }
}