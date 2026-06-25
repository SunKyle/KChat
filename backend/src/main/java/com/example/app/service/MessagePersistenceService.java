
package com.example.app.service;

import com.example.app.entity.Message;
import com.example.app.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagePersistenceService {

    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public String saveUserMessage(String conversationId, String content, List<String> imageUrls) {
        String messageId = UUID.randomUUID().toString();
        String imagesJson = serializeImageUrls(imageUrls);
        
        Message userMsg = Message.builder()
                .id(messageId)
                .conversationId(conversationId)
                .content(content)
                .role("user")
                .images(imagesJson)
                .build();
        
        messageRepository.save(userMsg);
        log.debug("Saved user message: conversationId={}, messageId={}", conversationId, messageId);
        return messageId;
    }

    @Transactional
    public String saveAiMessage(String conversationId, String content) {
        return saveAiMessage(conversationId, UUID.randomUUID().toString(), content);
    }

    @Transactional
    public String saveAiMessage(String conversationId, String messageId, String content) {
        Message aiMsg = Message.builder()
                .id(messageId)
                .conversationId(conversationId)
                .content(content)
                .role("assistant")
                .build();
        
        messageRepository.save(aiMsg);
        log.debug("Saved AI message: conversationId={}, messageId={}", conversationId, messageId);
        return messageId;
    }

    @Transactional
    public String saveMessages(String conversationId, String userMessage, String aiResponse, List<String> imageUrls) {
        saveUserMessage(conversationId, userMessage, imageUrls);
        String aiMessageId = saveAiMessage(conversationId, aiResponse);
        log.debug("Saved conversation messages: conversationId={}", conversationId);
        return aiMessageId;
    }

    private String serializeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(imageUrls);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize image URLs", e);
            return null;
        }
    }
}
