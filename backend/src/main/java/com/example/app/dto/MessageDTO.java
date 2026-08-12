package com.example.app.dto;

import com.example.app.entity.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    private String id;
    private String conversationId;
    private String content;
    private String role;
    private String timestamp;
    private List<String> images;
    private List<Artifact> artifacts;
    private List<Map<String, Object>> agentThinking;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static MessageDTO fromEntity(Message message) {
        List<String> images = new ArrayList<>();
        List<Artifact> artifacts = new ArrayList<>();
        List<Map<String, Object>> agentThinking = new ArrayList<>();
        if (message.getImages() != null && !message.getImages().isEmpty()) {
            try {
                images = OBJECT_MAPPER.readValue(message.getImages(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                images = new ArrayList<>();
            }
        }
        if (message.getArtifacts() != null && !message.getArtifacts().isEmpty()) {
            try {
                artifacts = OBJECT_MAPPER.readValue(
                        message.getArtifacts(), new TypeReference<List<Artifact>>() {});
            } catch (JsonProcessingException e) {
                artifacts = new ArrayList<>();
            }
        }
        if (message.getAgentThinking() != null && !message.getAgentThinking().isEmpty()) {
            try {
                agentThinking = OBJECT_MAPPER.readValue(
                        message.getAgentThinking(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (JsonProcessingException e) {
                agentThinking = new ArrayList<>();
            }
        }
        if (images.isEmpty() && !artifacts.isEmpty()) {
            images = artifacts.stream()
                    .filter(a -> "image".equals(a.type()))
                    .map(Artifact::url)
                    .toList();
        }
        return MessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .content(message.getContent())
                .role(message.getRole())
                .timestamp(message.getTimestamp().format(FORMATTER))
                .images(images)
                .artifacts(artifacts)
                .agentThinking(agentThinking)
                .build();
    }
}
