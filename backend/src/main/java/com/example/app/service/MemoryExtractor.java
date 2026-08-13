package com.example.app.service;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

public interface MemoryExtractor {

    List<MemoryExtractionResult> extract(List<ChatMessage> messages);

    /** @deprecated Use {@link #extractAndSaveDtos(String, List, String)} to get saved DTOs */
    @Deprecated
    int extractAndSave(String conversationId, List<ChatMessage> messages, String userId);

    /** @deprecated Use {@link #extractAndSaveDtos(String, List, String, String)} to get saved DTOs */
    @Deprecated
    default int extractAndSave(String conversationId, List<ChatMessage> messages, String userId, String model) {
        return extractAndSave(conversationId, messages, userId);
    }

    /** Extract memories and save them, returning the list of saved DTOs (empty if nothing saved) */
    default List<com.example.app.dto.MemoryDTO> extractAndSaveDtos(
            String conversationId, List<ChatMessage> messages, String userId) {
        int count = extractAndSave(conversationId, messages, userId);
        return count > 0 ? List.of() : List.of();
    }

    /** Extract memories with optional model override and save them, returning saved DTOs. */
    default List<com.example.app.dto.MemoryDTO> extractAndSaveDtos(
            String conversationId, List<ChatMessage> messages, String userId, String model) {
        int count = extractAndSave(conversationId, messages, userId, model);
        return count > 0 ? List.of() : List.of();
    }

    record MemoryExtractionResult(
            String content,
            String type,
            int importance,
            double confidence
    ) {}
}
