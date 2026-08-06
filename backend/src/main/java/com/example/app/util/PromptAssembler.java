package com.example.app.util;

import com.example.app.config.DefaultSystemPrompt;
import com.example.app.dto.MemoryDTO;
import com.example.app.entity.PromptMetrics;
import com.example.app.security.InputValidator;
import com.example.app.security.SensitiveFilter;
import com.example.app.service.PromptMetricsService;
import com.example.app.service.PromptTemplateService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Prompt 组装工具
 *
 * 负责将短期记忆、长期记忆和用户输入组装成 LLM 可理解的消息序列。
 *
 * @deprecated Since Phase 2 of the Context Pipeline migration.
 * This class remains functional for backward compatibility but new features
 * should use {@link com.example.app.pipeline.ContextPipelineExecutor} with
 * individual {@link com.example.app.pipeline.ContextPipelineStage} beans instead.
 * The pipeline decomposes the monolithic 7-step assembly into independently
 * testable, composable stages.
 */
@Deprecated
@Component
@Slf4j
public class PromptAssembler {

    /**
     * 默认最大 Token 限制
     */
    @Value("${prompt.token.max-tokens:8192}")
    private int defaultMaxTokens;

    /**
     * 默认系统模板名称
     */
    @Value("${prompt.template.default-id:default-system-prompt}")
    private String defaultTemplateName;

    /**
     * 是否启用安全过滤
     */
    @Value("${prompt.security.enable-sanitize:true}")
    private boolean enableSanitize;

    /**
     * 是否启用指标记录
     */
    @Value("${prompt.metrics.enabled:true}")
    private boolean enableMetrics;

    /**
     * Token 估算器
     */
    private final TokenEstimator tokenEstimator;

    /**
     * 输入验证器（安全过滤）
     */
    private final InputValidator inputValidator;

    /**
     * 敏感信息过滤器
     */
    private final SensitiveFilter sensitiveFilter;

    /**
     * 模板服务（动态加载模板）
     */
    private final PromptTemplateService templateService;

    /**
     * 指标服务（记录监控指标）
     */
    private final PromptMetricsService metricsService;

    /**
     * 核心 System Prompt 模板（默认硬编码，可被数据库模板覆盖）
     *
     * 占位符说明：
     * - {language_clause}: 语言偏好指令（如 "请使用中文（简体）回复。"），无偏好时为空字符串
     * - {user_profile}: 用户档案（可信事实，含 "用户档案（可信，由系统维护）：..." 标签），无档案时为空字符串
     * - {long_term_memory}: 用户长期记忆（含 "长期记忆（可能过时，仅作参考）：..." 标签与时间/置信度/来源），无记忆时为空字符串
     * - {search_context}: 网络搜索上下文（含时间戳和搜索结果），无搜索时为空字符串
     */
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
            java.util.Map.entry("ru", "Русский"));

    public PromptAssembler(TokenEstimator tokenEstimator,
            InputValidator inputValidator,
            SensitiveFilter sensitiveFilter,
            PromptTemplateService templateService,
            PromptMetricsService metricsService) {
        this.tokenEstimator = tokenEstimator;
        this.inputValidator = inputValidator;
        this.sensitiveFilter = sensitiveFilter;
        this.templateService = templateService;
        this.metricsService = metricsService;
    }

    /**
     * 组装最终发送给 LLM 的消息序列
     *
     * 拼接顺序（优先级从高到低）：
     * 1. SystemMessage (包含语言指令和长期记忆)：设定全局认知基调
     * 2. ShortTermMemory (对话历史)：维持会话连贯性
     * 3. UserMessage (当前输入)：触发执行任务
     *
     * 集成功能：
     * - 安全过滤：用户输入校验和敏感信息脱敏
     * - 动态模板：从数据库加载系统提示词模板
     * - 指标记录：记录 Token 数量、构建耗时等
     *
     * @param shortTermMemory 短期记忆（对话历史）
     * @param longTermMemory  长期记忆（召回的知识片段）
     * @param userMessage     当前用户输入
     * @param language        用户语言偏好（如 "zh-CN", "en"），为 null 时不注入语言指令
     * @return 组装好的消息列表
     */
    public List<ChatMessage> assemble(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage,
            String language) {
        return assemble(shortTermMemory, longTermMemory, userMessage, language, null, null);
    }

    public List<ChatMessage> assemble(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage,
            String language,
            String searchContext) {
        return assemble(shortTermMemory, longTermMemory, userMessage, language, null, searchContext);
    }

    /**
     * 组装最终发送给 LLM 的消息序列（带会话ID用于指标记录）
     *
     * @param shortTermMemory 短期记忆（对话历史）
     * @param longTermMemory  长期记忆（召回的知识片段）
     * @param userMessage     当前用户输入
     * @param language        用户语言偏好
     * @param conversationId  会话ID（用于指标记录，可为null）
     * @return 组装好的消息列表
     */
    public List<ChatMessage> assemble(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage,
            String language,
            String conversationId,
            String searchContext) {

        long startTime = System.currentTimeMillis();

        try {
            List<ChatMessage> messages = new ArrayList<>();

            String sanitizedUserMessage = sanitizeInput(userMessage);
            String languageClause = buildLanguageClause(language);
            String longTermMemoryText = formatLongTermMemory(longTermMemory);
            String systemPrompt = buildSystemPrompt(languageClause, longTermMemoryText, searchContext);
            messages.add(SystemMessage.from(systemPrompt));

            if (shortTermMemory != null) {
                messages.addAll(shortTermMemory);
            }

            messages.add(UserMessage.from(sanitizedUserMessage));

            if (enableMetrics) {
                recordMetrics(messages, longTermMemory, startTime, conversationId, false);
            }

            return messages;

        } catch (Exception e) {
            log.error("[Prompt组装] 失败 - 会话: {}, 错误: {}", conversationId, e.getMessage(), e);
            return fallbackAssemble(userMessage, language);
        }
    }

    /**
     * 安全过滤用户输入
     */
    private String sanitizeInput(String input) {
        if (!enableSanitize || input == null) {
            return input;
        }
        try {
            return inputValidator.validateAndSanitize(input);
        } catch (IllegalArgumentException e) {
            log.warn("Input validation failed: {}", e.getMessage());
            return input; // 返回原始输入，让上层处理错误
        }
    }

    /**
     * 构建系统提示词（动态加载或使用默认模板）
     *
     * 模板占位符：{language_clause}、{long_term_memory}、{search_context}
     * 搜索上下文通过 {search_context} 占位符注入模板中，而非硬拼接在末尾
     */
    private String buildSystemPrompt(String languageClause, String longTermMemoryText, String searchContext) {
        Map<String, String> params = new HashMap<>();
        params.put("language_clause", languageClause);
        params.put("long_term_memory", longTermMemoryText);
        params.put("search_context", buildSearchContextSection(searchContext));
        params.put("user_profile", "");

        String systemPrompt;
        try {
            systemPrompt = templateService.renderTemplate(defaultTemplateName, params);
        } catch (IllegalArgumentException e) {
            log.warn("Template not found in database, using fallback template: {}", e.getMessage());
            systemPrompt = DefaultSystemPrompt.CONTENT
                    .replace("{language_clause}", languageClause)
                    .replace("{user_profile}", "")
                    .replace("{long_term_memory}", longTermMemoryText)
                    .replace("{search_context}", params.get("search_context"));
        }

        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是一个智能助手。请根据上下文回答问题。";
        }

        return systemPrompt;
    }

    /**
     * 构建搜索上下文段（含当前时间），无搜索时返回空字符串
     */
    private String buildSearchContextSection(String searchContext) {
        if (searchContext == null || searchContext.isBlank()) {
            return "";
        }
        String now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss EEEE"));
        return "当前时间：" + now
                + "\n\n网络搜索结果：\n" + searchContext
                + "\n\n请基于以上网络搜索结果回答用户问题。如果搜索结果不足以回答问题，请结合你的知识进行补充。";
    }

    /**
     * 记录监控指标
     */
    private void recordMetrics(List<ChatMessage> messages, List<MemoryDTO> longTermMemory,
            long startTime, String conversationId, boolean truncated) {
        try {
            long buildDuration = System.currentTimeMillis() - startTime;
            int tokenCount = calculateTokenCount(messages);
            int memoryCount = longTermMemory != null ? longTermMemory.size() : 0;

            metricsService.recordMetrics(conversationId != null ? conversationId : "unknown",
                    tokenCount, memoryCount, buildDuration, truncated);

            log.debug("Metrics recorded: tokens={}, duration={}ms, memories={}",
                    tokenCount, buildDuration, memoryCount);
        } catch (Exception e) {
            log.warn("Failed to record metrics: {}", e.getMessage());
        }
    }

    /**
     * 降级组装（当主要流程失败时使用）
     */
    private List<ChatMessage> fallbackAssemble(String userMessage, String language) {
        log.warn("[Prompt组装] 降级模式激活 - 语言: {}", language);

        List<ChatMessage> messages = new ArrayList<>();
        String languageClause = buildLanguageClause(language);
        String systemPrompt = "你是一个智能助手。" + (languageClause.isEmpty() ? "" : languageClause);
        messages.add(SystemMessage.from(systemPrompt));
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
        return assemble(shortTermMemory, longTermMemory, userMessage, null, null, null);
    }

    /**
     * 组装消息并自动截断到 Token 限制
     *
     * @param shortTermMemory 短期记忆（对话历史）
     * @param longTermMemory  长期记忆（召回的知识片段）
     * @param userMessage     当前用户输入
     * @param language        用户语言偏好
     * @param maxTokens       最大 Token 限制
     * @return 组装并截断后的消息列表
     */
    public List<ChatMessage> assembleWithTruncation(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage,
            String language,
            int maxTokens) {
        return assembleWithTruncation(shortTermMemory, longTermMemory, userMessage, language, maxTokens, null);
    }

    /**
     * 组装消息并自动截断到 Token 限制（带会话ID）
     */
    public List<ChatMessage> assembleWithTruncation(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage,
            String language,
            int maxTokens,
            String conversationId) {

        List<ChatMessage> messages = assemble(shortTermMemory, longTermMemory, userMessage, language, conversationId, null);

        if (calculateTokenCount(messages) <= maxTokens) {
            return messages;
        }

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
        return assembleWithTruncation(shortTermMemory, longTermMemory, userMessage, language, defaultMaxTokens, null);
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
     * 格式化长期记忆为自然语言列表
     *
     * 以纯自然语言呈现，不暴露内部元数据（类型枚举、重要性分数）给 LLM。
     * 无记忆时返回空字符串，由模板中的 {long_term_memory} 占位符整体替换为空。
     *
     * @param memories 记忆 DTO 列表
     * @return 带标签的记忆列表文本，无记忆时返回空字符串
     */
    private String formatLongTermMemory(List<MemoryDTO> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        List<MemoryDTO> sortedMemories = new ArrayList<>(memories);
        sortedMemories.sort((a, b) -> Integer.compare(b.getImportance(), a.getImportance()));

        StringBuilder sb = new StringBuilder();
        sb.append("长期记忆（可能过时，仅作参考）：\n");
        for (MemoryDTO memory : sortedMemories) {
            sb.append("- ");
            LocalDateTime time = memory.getUpdatedAt() != null ? memory.getUpdatedAt() : memory.getCreatedAt();
            if (time != null) {
                sb.append("[").append(time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).append("] ");
            }
            sb.append(memory.getContent());
            List<String> tags = new ArrayList<>();
            if (memory.getConfidence() != null) {
                tags.add("置信度 " + Math.round(memory.getConfidence() * 100) + "%");
            }
            if (memory.getSource() != null && !memory.getSource().isBlank()) {
                tags.add("来源 " + memory.getSource());
            }
            if (!tags.isEmpty()) {
                sb.append("（").append(String.join("，", tags)).append("）");
            }
            sb.append("\n");
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
     * @param messages  原始消息列表
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
     * @param messages  消息列表
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
