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
public class ChatRequest {

    private String conversationId;

    private String message;

    private String model;

    private List<String> imageUrls;
    
    private String userId;

    private boolean webSearch = false;

    private boolean agentMode = false;
}
