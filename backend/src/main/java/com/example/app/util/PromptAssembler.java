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
     * - 使用占位符 {language_instruction} 注入语言偏好指令
     * - 引导 LLM 将这些事实作为用户的背景知识，而非对话历史
     * - 不直接暴露内部实现细节给 LLM
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个智能助手。{language_clause}请根据以下用户背景信息回答问题。

            用户背景：
            {long_term_memory}
            """;

    /**
     * 语言代码到自然语言描述的映射
     */
    private static final java.util.Map<String, String> LANGUAGE_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("zh-CN", "中文（简体）"),
            java.util.Map.entry("zh-TW", "中文（繁體）"),
            java.util.Map.entry("en", "English"),
            java.util.Map.entry("en-US", "English"),
            java.util.Map.entry("en-GB", "English"),
            java.util.Map.entry("ja", "日本語"),
            java.util.Map.entry("ko", "한국어"),
            java.util.Map.entry("fr", "Français"),
            java.util.Map.entry("de", "Deutsch"),
            java.util.Map.entry("es", "Español"),
            java.util.Map.entry("ru", "Русский")
    );

    /**
     * 组装最终发送给 LLM 的消息序列
     *
     * 拼接顺序（优先级从高到低）：
     * 1. SystemMessage (包含语言指令和长期记忆)：设定全局认知基调
     * 2. ShortTermMemory (对话历史)：维持会话连贯性
     * 3. UserMessage (当前输入)：触发执行任务
     *
     * @param shortTermMemory 短期记忆（对话历史）
     * @param longTermMemory 长期记忆（召回的知识片段）
     * @param userMessage 当前用户输入
     * @param language 用户语言偏好（如 "zh-CN", "en"），为 null 时不注入语言指令
     * @return 组装好的消息列表
     */
    public List<ChatMessage> assemble(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage,
            String language) {

        List<ChatMessage> messages = new ArrayList<>();

        // 构建语言指令
        String languageClause = buildLanguageClause(language);

        // 构建长期记忆文本
        String longTermMemoryText = formatLongTermMemory(longTermMemory);

        // 始终注入 System Prompt（包含语言指令和记忆信息）
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE
                .replace("{language_clause}", languageClause)
                .replace("{long_term_memory}", longTermMemoryText);
        messages.add(SystemMessage.from(systemPrompt));

        if (shortTermMemory != null) {
            messages.addAll(shortTermMemory);
        }

        messages.add(UserMessage.from(userMessage));

        return messages;
    }

    /**
     * 向后兼容的无语言参数版本
     */
    public List<ChatMessage> assemble(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage) {
        return assemble(shortTermMemory, longTermMemory, userMessage, null);
    }

    /**
     * 根据语言偏好构建语言从句，嵌入到 system prompt 句子中
     *
     * @param language 语言代码（如 "zh-CN"、"en"）
     * @return 语言从句字符串，如 "请使用中文（简体）回复。"
     */
    private String buildLanguageClause(String language) {
        if (language == null || language.isBlank()) {
            return "";
        }
        String languageName = LANGUAGE_NAMES.getOrDefault(language, language);
        return "请使用 " + languageName + " 回复。";
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
