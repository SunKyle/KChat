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
    private List<KbReference> kbReferences;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static MessageDTO fromEntity(Message message) {
        List<String> images = new ArrayList<>();
        List<Artifact> artifacts = new ArrayList<>();
        List<Map<String, Object>> agentThinking = new ArrayList<>();
        List<KbReference> kbReferences = parseKbReferences(message.getKbReferences());
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
                .kbReferences(kbReferences)
                .build();
    }

    /**
     * 解析 Message.kbReferences JSON 列。
     *
     * <p>兼容两种历史格式：
     * <ul>
     *   <li>新格式 {@code [{"kbName":"知识库A","docName":"文档B"}]} → 直接反序列化为 KbReference</li>
     *   <li>旧格式 {@code ["知识库A"]}（升级前的纯字符串数组）→ 包装为仅知识库层级的 KbReference</li>
     * </ul>
     */
    private static List<KbReference> parseKbReferences(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        // 新格式
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<KbReference>>() {});
        } catch (JsonProcessingException ignored) {
            // 旧格式
        }
        try {
            List<String> names = OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
            return names.stream().map(KbReference::of).collect(java.util.stream.Collectors.toList());
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
