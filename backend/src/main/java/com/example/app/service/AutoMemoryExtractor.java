package com.example.app.service;

import com.example.app.config.MemoryExtractorConfig;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自动化记忆提取服务。
 * 将非结构化的对话历史转化为结构化的长期记忆事实。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoMemoryExtractor {

    private final MemoryExtractor memoryExtractor;
    private final MemoryService memoryService;
    private final ConversationMessageCounter messageCounter;
    private final MemoryExtractorConfig config;

    /**
     * 尝试提取记忆。
     * <p>
     * 设计考量：
     * 1. 避免频繁调用 LLM 造成成本浪费 $\rightarrow$ 通过 messageCounter 实现基于消息数量的阈值触发。
     * 2. 降低响应延迟 $\rightarrow$ 建议调用方在异步线程中执行此方法。
     */
    public int tryExtract(String conversationId, String userId) {
        if (!config.isEnabled() || !config.isAutoExtractEnabled()) {
            return 0;
        }

        // 使用原子计数器/缓存计数器记录当前会话消息数，达到阈值后才触发 LLM 分析
        int messageCount = messageCounter.increment(conversationId);

        if (messageCount >= config.getMessageThreshold()) {
            messageCounter.reset(conversationId);
            return extractAndSave(conversationId, userId);
        }

        return 0;
    }

    /**
     * 记忆提取核心逻辑。
     * 流程：获取上下文 $\rightarrow$ LLM 总结事实 $\rightarrow$ 向量化存储。
     */
    public int extractAndSave(String conversationId, String userId) {
        try {
            List<ChatMessage> messages = memoryService.getMemoryContext(conversationId);
            return memoryExtractor.extractAndSave(conversationId, messages, userId);
        } catch (Exception e) {
            log.error("Critical failure during memory extraction for user {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    /**
     * 定时扫描空闲对话。
     * 业务目的：对于长时间未活跃但未达到阈值的对话，在用户离开后补齐记忆提取，防止知识丢失。
     */
    @Scheduled(fixedDelay = 60000)
    public void checkIdleConversations() {
        if (!config.isEnabled()) {
            return;
        }
        // TODO: 实现空闲对话检索逻辑
    }
}
