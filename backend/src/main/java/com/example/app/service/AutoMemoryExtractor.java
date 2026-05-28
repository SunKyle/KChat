package com.example.app.service;

import com.example.app.config.MemoryExtractorConfig;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自动记忆提取服务类
 * 负责在满足条件时自动从对话中提取和保存记忆
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoMemoryExtractor {


    // 依赖注入的组件：
    private final MemoryExtractor memoryExtractor;      // 记忆提取器
    private final MemoryService memoryService;         // 记忆服务
    private final ConversationMessageCounter messageCounter;  // 对话消息计数器
    private final MemoryExtractorConfig config;        // 记忆提取配置

    /**
     * 尝试提取对话中的记忆
     *
     * @param conversationId 对话ID
     * @param userId         用户ID
     * @return 成功提取并保存的记忆数量
     */
    public int tryExtract(String conversationId, String userId) {
        log.info("[AutoMemoryExtractor] tryExtract called - conversation: {}, userId: {}", conversationId, userId);

        // 检查记忆提取器是否启用
        if (!config.isEnabled()) {
            log.info("[AutoMemoryExtractor] Extractor is disabled");
            return 0;
        }

        // 检查自动提取功能是否启用
        if (!config.isAutoExtractEnabled()) {
            log.info("[AutoMemoryExtractor] Auto-extract is disabled");
            return 0;
        }

        // 增加消息计数并检查是否达到阈值
        int messageCount = messageCounter.increment(conversationId);
        log.info("[AutoMemoryExtractor] Message count: {}, threshold: {}", messageCount, config.getMessageThreshold());

        // 如果消息数量达到阈值，执行提取
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

    /**
     * 提取并保存对话中的记忆
     *
     * @param conversationId 对话ID
     * @param userId         用户ID
     * @return 成功保存的记忆数量
     */
    public int extractAndSave(String conversationId, String userId) {
        try {
            // 获取对话上下文并提取记忆
            List<ChatMessage> messages = memoryService.getMemoryContext(conversationId);
            int saved = memoryExtractor.extractAndSave(conversationId, messages, userId);

            // 记录提取结果
            if (saved > 0) {
                log.info("Auto-extracted {} memories for conversation {} (user: {})",
                        saved, conversationId, userId);
            }

            return saved;
        } catch (Exception e) {
            // 异常处理
            log.error("Failed to auto-extract memory for conversation {}: {}",
                    conversationId, e.getMessage());
            return 0;
        }
    }

    /**
     * 定时任务：检查空闲的对话
     * 每分钟执行一次
     */
    @Scheduled(fixedDelay = 60000)
    public void checkIdleConversations() {
        // 检查功能是否启用
        if (!config.isEnabled()) {
            return;
        }

        // 计算空闲超时阈值
        long idleThreshold = config.getIdleTimeoutMinutes() * 60 * 1000;
        long now = System.currentTimeMillis();

        log.debug("Checking for idle conversations...");
    }
}