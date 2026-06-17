package com.example.app.util;

import com.example.app.dto.MemoryDTO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 组装工具
 *
 * 负责将短期记忆、长期记忆和用户输入组装成 LLM 可理解的消息序列
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptAssembler {

    /**
     * 默认最大 Token 限制
     */
    @Value("${prompt.token.max-tokens:8192}")
    private int defaultMaxTokens;

    /**
     * Token 估算器
     */
    private final TokenEstimator tokenEstimator;

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
     * 组装消息并自动截断到 Token 限制
     *
     * @param shortTermMemory 短期记忆（对话历史）
     * @param longTermMemory 长期记忆（召回的知识片段）
     * @param userMessage 当前用户输入
     * @param language 用户语言偏好
     * @param maxTokens 最大 Token 限制
     * @return 组装并截断后的消息列表
     */
    public List<ChatMessage> assembleWithTruncation(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage,
            String language,
            int maxTokens) {
        
        List<ChatMessage> messages = assemble(shortTermMemory, longTermMemory, userMessage, language);
        return truncateToTokenLimit(messages, maxTokens);
    }

    /**
     * 组装消息并自动截断到默认 Token 限制
     */
    public List<ChatMessage> assembleWithTruncation(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage,
            String language) {
        return assembleWithTruncation(shortTermMemory, longTermMemory, userMessage, language, defaultMaxTokens);
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
     * 计算 Token 数量（使用 TokenEstimator）
     *
     * @param messages 消息列表
     * @return 估算的 Token 数量
     */
    public int calculateTokenCount(List<ChatMessage> messages) {
        return tokenEstimator.estimate(messages);
    }

    /**
     * 计算单个消息的 Token 数量
     *
     * @param message 消息
     * @return 估算的 Token 数量
     */
    public int calculateTokenCount(ChatMessage message) {
        return tokenEstimator.estimate(message);
    }

    /**
     * 智能截断消息序列以适应 Token 限制
     *
     * 截断策略：
     * - 始终保留 SystemMessage（系统指令是核心配置）
     * - 始终保留当前用户输入（最后一条 UserMessage）
     * - 从后往前保留历史对话，优先保留最近的交互
     * - 确保整体不超过 Token 限制
     *
     * @param messages 原始消息列表
     * @param maxTokens 最大 Token 限制
     * @return 截断后的消息列表
     */
    public List<ChatMessage> truncateToTokenLimit(List<ChatMessage> messages, int maxTokens) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        // 分离消息类型
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> historyMessages = new ArrayList<>();
        ChatMessage lastUserMessage = null;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof SystemMessage) {
                systemMessages.add(msg);
            } else if (msg instanceof UserMessage && i == messages.size() - 1) {
                lastUserMessage = msg;
            } else {
                historyMessages.add(msg);
            }
        }

        // 计算必须保留的消息的 Token 消耗
        int systemTokens = tokenEstimator.estimate(systemMessages);
        int userTokens = lastUserMessage != null ? tokenEstimator.estimate(lastUserMessage) : 0;
        int mandatoryTokens = systemTokens + userTokens;

        log.debug("Token breakdown - System: {}, User: {}, Mandatory: {}, Max: {}", 
                systemTokens, userTokens, mandatoryTokens, maxTokens);

        // 如果必须保留的消息已经超过限制，需要进行极端处理
        if (mandatoryTokens > maxTokens) {
            log.warn("Mandatory tokens ({}) exceed max tokens ({})", mandatoryTokens, maxTokens);
            
            // 尝试只保留最重要的部分
            List<ChatMessage> minimalResult = new ArrayList<>();
            
            // 保留至少一条 SystemMessage
            if (!systemMessages.isEmpty()) {
                minimalResult.add(systemMessages.get(0));
            }
            
            // 如果还有空间，保留用户消息
            int remainingAfterSystem = maxTokens - tokenEstimator.estimate(minimalResult);
            if (lastUserMessage != null && tokenEstimator.estimate(lastUserMessage) <= remainingAfterSystem) {
                minimalResult.add(lastUserMessage);
            }
            
            log.warn("Returning minimal message set due to token constraints");
            return minimalResult;
        }

        // 计算可用于历史消息的 Token 额度
        int availableTokens = maxTokens - mandatoryTokens;
        
        // 从后往前选择历史消息（优先保留最近的）
        List<ChatMessage> selectedHistory = new ArrayList<>();
        int currentHistoryTokens = 0;
        
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = historyMessages.get(i);
            int msgTokens = tokenEstimator.estimate(msg);
            
            if (currentHistoryTokens + msgTokens <= availableTokens) {
                selectedHistory.add(0, msg);
                currentHistoryTokens += msgTokens;
            } else {
                log.debug("Skipping message due to token limit");
            }
        }

        // 组装最终结果
        List<ChatMessage> result = new ArrayList<>();
        result.addAll(systemMessages);
        result.addAll(selectedHistory);
        if (lastUserMessage != null) {
            result.add(lastUserMessage);
        }

        int totalTokens = tokenEstimator.estimate(result);
        log.debug("Truncated to {} messages, total tokens: {}", result.size(), totalTokens);

        return result;
    }

    /**
     * 判断是否需要截断
     *
     * @param messages 消息列表
     * @param maxTokens 最大 Token 限制
     * @return true 如果需要截断
     */
    public boolean needsTruncation(List<ChatMessage> messages, int maxTokens) {
        return calculateTokenCount(messages) > maxTokens;
    }

    /**
     * 获取默认最大 Token 限制
     */
    public int getDefaultMaxTokens() {
        return defaultMaxTokens;
    }
}