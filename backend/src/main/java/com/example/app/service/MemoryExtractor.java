package com.example.app.service;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

public interface MemoryExtractor {

    List<MemoryExtractionResult> extract(List<ChatMessage> messages);

    int extractAndSave(String conversationId, List<ChatMessage> messages, String userId);

    record MemoryExtractionResult(
            String content,
            String type,
            int importance,
            double confidence
    ) {}
}