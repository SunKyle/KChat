package com.example.app.service;

import com.example.app.config.MemoryExtractorConfig;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自动化记忆提取服务
 *
 * 核心职责：
 * - 将非结构化的对话历史转化为结构化的长期记忆事实
 * - 通过阈值控制平衡提取质量和 LLM 调用成本
 * - 支持定时补提空闲对话的记忆
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoMemoryExtractor {

    private final MemoryExtractor memoryExtractor;
    private final ShortTermMemoryService shortTermMemoryService;
    private final ConversationMessageCounter messageCounter;
    private final MemoryExtractorConfig config;

    /**
     * 尝试提取记忆
     *
     * 设计考虑：
     * 1. 避免频繁调用 LLM 造成成本浪费：通过 messageCounter 实现基于消息数量的阈值触发
     * 2. 降低响应延迟：建议调用方在异步线程中执行此方法
     *
     * @param conversationId 对话 ID
     * @param userId         用户 ID
     * @return 提取的记忆数量，未触发提取返回 0
     */
    public int tryExtract(String conversationId, String userId) {
        return tryExtract(conversationId, userId, null);
    }

    public int tryExtract(String conversationId, String userId, String model) {
        log.info("[记忆提取] 尝试提取 - 会话: {}, 用户: {}", conversationId, userId);

        if (!config.isEnabled()) {
            log.info("[记忆提取] 未触发 - 记忆提取功能已禁用");
            return 0;
        }

        if (!config.isAutoExtractEnabled()) {
            log.info("[记忆提取] 未触发 - 自动提取已禁用");
            return 0;
        }

        int messageCount = messageCounter.increment(conversationId);
        int threshold = config.getMessageThreshold();

        log.info("[记忆提取] 当前消息数: {}, 触发阈值: {}", messageCount, threshold);

        if (messageCount >= threshold) {
            log.info("[记忆提取] 达到阈值，开始提取...");
            messageCounter.reset(conversationId);
            int extracted = extractAndSave(conversationId, userId, model);
            log.info("[记忆提取] 提取完成 - 保存了 {} 条记忆", extracted);
            return extracted;
        }

        log.info("[记忆提取] 未达到阈值，等待更多消息...");
        return 0;
    }

    /**
     * 记忆提取核心逻辑
     *
     * 流程：获取上下文 -> LLM 总结事实 -> 去重和筛选 -> 向量化存储
     *
     * 异常处理：
     * - 捕获所有异常，保证不影响主对话流程
     * - 记录错误日志便于排查
     *
     * @param conversationId 对话 ID
     * @param userId         用户 ID
     * @return 保存的记忆数量
     */
    public int extractAndSave(String conversationId, String userId) {
        return extractAndSave(conversationId, userId, null);
    }

    public int extractAndSave(String conversationId, String userId, String model) {
        try {
            List<ChatMessage> messages = shortTermMemoryService.getMemoryContext(conversationId);
            return memoryExtractor.extractAndSave(conversationId, messages, userId, model);
        } catch (Exception e) {
            log.error("Critical failure during memory extraction for user {}: {}", userId, e.getMessage());
            return 0;
        }
    }

    /**
     * 定时扫描空闲对话
     *
     * 业务目的：
     * 对于长时间未活跃但未达到阈值的对话，在用户离开后补齐记忆提取，防止知识丢失
     *
     * TODO: 实现空闲对话检索逻辑
     * 技术债务：
     * - 当前为占位实现，未实际执行任何操作
     * - 需要实现：1. 查询空闲超过 idleTimeoutMinutes 的对话；2. 触发记忆提取
     */
    @Scheduled(fixedDelay = 60000)
    public void checkIdleConversations() {
        if (!config.isEnabled()) {
            return;
        }
    }
}
