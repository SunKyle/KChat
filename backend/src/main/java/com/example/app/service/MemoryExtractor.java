package com.example.app.service;

import com.example.app.dto.MemoryDTO;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

public interface MemoryExtractor {

    List<MemoryExtractionResult> extract(List<ChatMessage> messages);

    /** Extract memories (filtered by confidence/importance thresholds) and return DTOs */
    List<MemoryDTO> extractDtos(String conversationId, List<ChatMessage> messages, String userId, String model);

    record MemoryExtractionResult(
            String content,
            String type,
            int importance,
            double confidence
    ) {}
}
