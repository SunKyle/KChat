package com.example.app.util;

import com.example.app.dto.MemoryDTO;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 组装工具
 *
 * 负责将短期记忆、长期记忆和用户输入组装成 LLM 可理解的消息序列
 */
@Component
public class PromptAssembler {

    /**
     * 核心 System Prompt 模板
     *
     * 设计考虑：
     * - 使用占位符 {long_term_memory} 注入召回的语义片段
     * - 引导 LLM 将这些事实作为用户的背景知识，而非对话历史
     * - 不直接暴露内部实现细节给 LLM
     */
    private static final String SYSTEM_PROMPT = """
            你是一个智能助手，请根据提供的信息回答用户问题。

            用户的长期记忆：
            {long_term_memory}

            请记住这些信息，并在回答时考虑用户的背景和偏好。
            """;

    /**
     * 组装最终发送给 LLM 的消息序列
     *
     * 拼接顺序（优先级从高到低）：
     * 1. SystemMessage (包含长期记忆)：设定全局认知基调
     * 2. ShortTermMemory (对话历史)：维持会话连贯性
     * 3. UserMessage (当前输入)：触发执行任务
     *
     * @param shortTermMemory 短期记忆（对话历史）
     * @param longTermMemory 长期记忆（召回的知识片段）
     * @param userMessage 当前用户输入
     * @return 组装好的消息列表
     */
    public List<ChatMessage> assemble(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage) {

        List<ChatMessage> messages = new ArrayList<>();

        String longTermMemoryText = formatLongTermMemory(longTermMemory);
        if (!longTermMemoryText.isEmpty()) {
            String systemPrompt = SYSTEM_PROMPT.replace("{long_term_memory}", longTermMemoryText);
            messages.add(SystemMessage.from(systemPrompt));
        }

        if (shortTermMemory != null) {
            messages.addAll(shortTermMemory);
        }

        messages.add(UserMessage.from(userMessage));

        return messages;
    }

    /**
     * 格式化长期记忆为可读文本
     *
     * @param memories 记忆 DTO 列表
     * @return 格式化后的字符串
     */
    private String formatLongTermMemory(List<MemoryDTO> memories) {
        if (memories == null || memories.isEmpty()) {
            return "无";
        }

        StringBuilder sb = new StringBuilder();
        for (MemoryDTO memory : memories) {
            sb.append("- [")
                    .append(memory.getType())
                    .append("] ")
                    .append(memory.getContent())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 粗略计算 Token 数量
     *
     * 估算方法：
     * - 基于 1 Token ≈ 4 字符的经验估算
     * - 仅适用于中文，英文通常是 1 Token ≈ 0.75 词
     *
     * 用途：
     * - 防止超出 LLM 上下文窗口限制
     * - 为截断策略提供依据
     *
     * @param messages 消息列表
     * @return 估算的 Token 数量
     */
    public int calculateTokenCount(List<ChatMessage> messages) {
        int count = 0;
        for (ChatMessage message : messages) {
            count += message.text().length() / 4;
        }
        return count;
    }

    /**
     * 截断消息序列以适应 Token 限制
     *
     * 截断策略：
     * - 保留最近的对话（LIFO），优先舍弃早期的历史记录
     * - 保证最新的交互上下文不丢失
     * - SystemMessage 总是保留（如果有）
     *
     * 技术债务：
     * - 当前实现未区分 SystemMessage 和其他消息，可能会截断 SystemMessage
     * - 建议改进：总是保留 SystemMessage，只截断历史对话
     *
     * @param messages 原始消息列表
     * @param maxTokens 最大 Token 限制
     * @return 截断后的消息列表
     */
    public List<ChatMessage> truncateToTokenLimit(List<ChatMessage> messages, int maxTokens) {
        List<ChatMessage> result = new ArrayList<>();
        int currentTokens = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            int messageTokens = message.text().length() / 4;

            if (currentTokens + messageTokens <= maxTokens) {
                result.add(0, message);
                currentTokens += messageTokens;
            } else {
                break;
            }
        }

        return result;
    }
}
