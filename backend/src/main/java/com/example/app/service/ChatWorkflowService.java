package com.example.app.service;

import com.example.app.dto.ChatRequest;
import com.example.app.dto.MemoryDTO;
import com.example.app.util.PromptAssembler;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatWorkflowService {

    private final ConversationService conversationService;
    private final ShortTermMemoryService shortTermMemoryService;
    private final LongTermMemoryFacadeService longTermMemoryFacadeService;
    private final PromptAssembler promptAssembler;

    public String getOrCreateConversationId(ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation("新对话").getId();
            log.info("[ChatWorkflow] Created new conversation: {}", conversationId);
        } else {
            log.info("[ChatWorkflow] Using existing conversation: {}", conversationId);
        }
        return conversationId;
    }

    public List<ChatMessage> getShortTermMemory(String conversationId) {
        return shortTermMemoryService.getMemoryContext(conversationId);
    }

    public List<MemoryDTO> recallLongTermMemory(String userId, String query, int topK) {
        return longTermMemoryFacadeService.recallLongTermMemory(userId, query, topK);
    }

    public List<ChatMessage> assembleMessages(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage) {
        return promptAssembler.assemble(shortTermMemory, longTermMemory, userMessage);
    }

    public void updateShortTermMemory(String conversationId, String userMessage, String aiMessage) {
        shortTermMemoryService.updateMemory(conversationId, userMessage, aiMessage);
    }

    public void updateShortTermMemoryWithUserMessage(String conversationId, String userMessage) {
        shortTermMemoryService.updateMemoryWithUserMessage(conversationId, userMessage);
    }

    public void updateShortTermMemoryWithAiMessage(String conversationId, String aiMessage) {
        shortTermMemoryService.updateMemoryWithAiMessage(conversationId, aiMessage);
    }
}
