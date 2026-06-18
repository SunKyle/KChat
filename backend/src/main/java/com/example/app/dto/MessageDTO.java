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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static MessageDTO fromEntity(Message message) {
        List<String> images = new ArrayList<>();
        if (message.getImages() != null && !message.getImages().isEmpty()) {
            try {
                images = OBJECT_MAPPER.readValue(message.getImages(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                images = new ArrayList<>();
            }
        }
        return MessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .content(message.getContent())
                .role(message.getRole())
                .timestamp(message.getTimestamp().format(FORMATTER))
                .images(images)
                .build();
    }
}