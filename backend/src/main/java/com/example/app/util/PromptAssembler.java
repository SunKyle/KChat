package com.example.app.util;

import com.example.app.dto.MemoryDTO;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PromptAssembler {

    /**
     * 核心 System Prompt 模板。
     * 使用占位符 {long_term_memory} 注入召回的语义片段，
     * 引导 LLM 将这些事实作为用户的背景知识，而非对话历史。
     */
    private static final String SYSTEM_PROMPT = """
            你是一个智能助手，请根据提供的信息回答用户问题。
                        
            用户的长期记忆：
            {long_term_memory}
                        
            请记住这些信息，并在回答时考虑用户的背景和偏好。
            """;

    /**
     * 组装最终发送给 LLM 的消息序列。
     * <p>
     * 拼接顺序（优先级）：
     * 1. SystemMessage (包含长期记忆) $\rightarrow$ 设定全局认知基调。
     * 2. ShortTermMemory (对话历史) $\rightarrow$ 维持会话连贯性。
     * 3. UserMessage (当前输入) $\rightarrow$ 触发执行任务。
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
     * 粗略计算 Token 数量。
     * 基于 1 Token $\approx$ 4 字符的经验估算，用于防止超出 LLM 上下文窗口限制。
     */
    public int calculateTokenCount(List<ChatMessage> messages) {
        int count = 0;
        for (ChatMessage message : messages) {
            count += message.text().length() / 4;
        }
        return count;
    }

    /**
     * 截断消息序列以适应 Token 限制。
     * 策略：保留最近的对话（LIFO），优先舍弃早期的历史记录。
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
