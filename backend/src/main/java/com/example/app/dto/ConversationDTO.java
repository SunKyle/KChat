package com.example.app.dto;

import com.example.app.entity.Conversation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {

    private String id;
    private String title;
    private List<MessageDTO> messages;
    private String createdAt;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static ConversationDTO fromEntity(Conversation conversation) {
        String createdAtStr = conversation.getCreatedAt() != null 
            ? conversation.getCreatedAt().format(FORMATTER) 
            : java.time.LocalDateTime.now().format(FORMATTER);
            
        return ConversationDTO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .createdAt(createdAtStr)
                .build();
    }

    public static ConversationDTO fromEntity(Conversation conversation, List<MessageDTO> messages) {
        String createdAtStr = conversation.getCreatedAt() != null 
            ? conversation.getCreatedAt().format(FORMATTER) 
            : java.time.LocalDateTime.now().format(FORMATTER);
            
        return ConversationDTO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .messages(messages)
                .createdAt(createdAtStr)
                .build();
    }
}
