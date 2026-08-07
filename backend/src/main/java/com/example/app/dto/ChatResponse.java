package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String messageId;

    private String content;

    private String role;

    private String conversationId;

    private String title;

    private List<String> images;

    private List<MultimodalArtifact> artifacts;
}
