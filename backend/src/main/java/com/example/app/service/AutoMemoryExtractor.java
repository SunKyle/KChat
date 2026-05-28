package com.example.app.service;

import com.example.app.config.MemoryExtractorConfig;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoMemoryExtractor {

    private final MemoryExtractor memoryExtractor;
    private final MemoryService memoryService;
    private final ConversationMessageCounter messageCounter;
    private final MemoryExtractorConfig config;

    public int tryExtract(String conversationId, String userId) {
        log.info("[AutoMemoryExtractor] tryExtract called - conversation: {}, userId: {}", conversationId, userId);
        
        if (!config.isEnabled()) {
            log.info("[AutoMemoryExtractor] Extractor is disabled");
            return 0;
        }
        
        if (!config.isAutoExtractEnabled()) {
            log.info("[AutoMemoryExtractor] Auto-extract is disabled");
            return 0;
        }

        int messageCount = messageCounter.increment(conversationId);
        log.info("[AutoMemoryExtractor] Message count: {}, threshold: {}", messageCount, config.getMessageThreshold());
        
        if (messageCount >= config.getMessageThreshold()) {
            log.info("[AutoMemoryExtractor] Threshold reached, triggering extraction");
            messageCounter.reset(conversationId);
            int saved = extractAndSave(conversationId, userId);
            log.info("[AutoMemoryExtractor] Extraction completed, saved {} memories", saved);
            return saved;
        }
        
        log.info("[AutoMemoryExtractor] Threshold not reached, skipping extraction");
        return 0;
    }

    public int extractAndSave(String conversationId, String userId) {
        try {
            List<ChatMessage> messages = memoryService.getMemoryContext(conversationId);
            int saved = memoryExtractor.extractAndSave(conversationId, messages, userId);
            
            if (saved > 0) {
                log.info("Auto-extracted {} memories for conversation {} (user: {})", 
                        saved, conversationId, userId);
            }
            
            return saved;
        } catch (Exception e) {
            log.error("Failed to auto-extract memory for conversation {}: {}", 
                    conversationId, e.getMessage());
            return 0;
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void checkIdleConversations() {
        if (!config.isEnabled()) {
            return;
        }

        long idleThreshold = config.getIdleTimeoutMinutes() * 60 * 1000;
        long now = System.currentTimeMillis();
        
        log.debug("Checking for idle conversations...");
    }
}