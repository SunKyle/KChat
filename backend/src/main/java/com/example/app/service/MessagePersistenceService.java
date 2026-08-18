
package com.example.app.service;

import com.example.app.entity.Message;
import com.example.app.dto.Artifact;
import com.example.app.dto.KbReference;
import com.example.app.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
        return saveAiMessage(conversationId, UUID.randomUUID().toString(), content, null, null);
    }

    @Transactional
    public String saveAiMessage(String conversationId, String messageId, String content) {
        return saveAiMessage(conversationId, messageId, content, null, null);
    }

    @Transactional
    public String saveAiMessage(String conversationId, String content, List<Artifact> artifacts) {
        return saveAiMessage(conversationId, UUID.randomUUID().toString(), content, artifacts, null);
    }

    @Transactional
    public String saveAiMessage(String conversationId, String messageId, String content,
            List<Artifact> artifacts) {
        return saveAiMessage(conversationId, messageId, content, artifacts, null);
    }

    @Transactional
    public String saveAiMessage(String conversationId, String messageId, String content,
            List<Artifact> artifacts, List<Map<String, Object>> agentThinkingSteps) {
        return saveAiMessage(conversationId, messageId, content, artifacts, agentThinkingSteps, null);
    }

    @Transactional
    public String saveAiMessage(String conversationId, String messageId, String content,
            List<Artifact> artifacts, List<Map<String, Object>> agentThinkingSteps,
            List<KbReference> kbReferences) {
        Message aiMsg = Message.builder()
                .id(messageId)
                .conversationId(conversationId)
                .content(content)
                .role("assistant")
                .artifacts(serializeArtifacts(artifacts))
                .agentThinking(serializeAgentThinking(agentThinkingSteps))
                .kbReferences(serializeKbReferences(kbReferences))
                .build();
        
        messageRepository.save(aiMsg);
        log.debug("Saved AI message: conversationId={}, messageId={}", conversationId, messageId);
        return messageId;
    }

    @Transactional
    public String saveMessages(String conversationId, String userMessage, String aiResponse, List<String> imageUrls) {
        return saveMessages(conversationId, userMessage, aiResponse, imageUrls, null);
    }

    @Transactional
    public String saveMessages(String conversationId, String userMessage, String aiResponse,
            List<String> imageUrls, List<Artifact> artifacts) {
        saveUserMessage(conversationId, userMessage, imageUrls);
        String aiMessageId = saveAiMessage(conversationId, aiResponse, artifacts);
        log.debug("Saved conversation messages: conversationId={}", conversationId);
        return aiMessageId;
    }

    private String serializeArtifacts(List<Artifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(artifacts);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize artifacts", e);
            return null;
        }
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

    private String serializeAgentThinking(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize agentThinking steps", e);
            return null;
        }
    }

    private String serializeKbReferences(List<KbReference> kbReferences) {
        if (kbReferences == null || kbReferences.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(kbReferences);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize kbReferences", e);
            return null;
        }
    }
}
