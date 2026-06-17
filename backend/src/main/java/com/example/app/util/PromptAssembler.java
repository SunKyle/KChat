package com.example.app.util;

import com.example.app.dto.MemoryDTO;
import com.example.app.entity.PromptMetrics;
import com.example.app.security.InputValidator;
import com.example.app.security.SensitiveFilter;
import com.example.app.service.PromptMetricsService;
import com.example.app.service.PromptTemplateService;
import dev.langchain4j.data.message.AiMessage;
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

/**
 * Prompt 组装工具
 *
 * 负责将短期记忆、长期记忆和用户输入组装成 LLM 可理解的消息序列
 * 
 * 核心功能：
 * - 安全过滤：输入校验和敏感信息脱敏
 * - 智能截断：优先保留 SystemMessage 和当前用户输入
 * - 动态模板：支持从数据库加载模板
 * - 指标监控：记录 Token 数量、构建耗时等指标
 */
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
     * 设计考虑：
     * - 使用占位符 {long_term_memory} 注入召回的语义片段
     * - 使用占位符 {language_clause} 注入语言偏好指令
     * - 引导 LLM 将这些事实作为用户的背景知识，而非对话历史
     * - 不直接暴露内部实现细节给 LLM
     */
    private static final String FALLBACK_SYSTEM_PROMPT_TEMPLATE = """
            角色：你是 KChat 智能助手，一个专业、友好的AI助手。

            核心指令：
            1. 始终使用 {language_clause}
            2. 基于提供的用户背景信息回答问题
            3. 回答要简洁明了，避免冗长
            4. 对于不确定的问题，诚实告知

            用户背景：
            {long_term_memory}

            开始回答：
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
        return assemble(shortTermMemory, longTermMemory, userMessage, language, null);
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
            String conversationId) {

        long startTime = System.currentTimeMillis();
        String sessionId = conversationId != null ? conversationId : "anonymous";

        log.info("========================================");
        log.info("[Prompt组装] 开始 - 会话: {}", sessionId);
        log.info("[Prompt组装] 时间戳: {}", java.time.LocalDateTime.now());

        try {
            List<ChatMessage> messages = new ArrayList<>();

            // 1. 安全过滤：校验和净化用户输入
            log.info("[步骤 1/7] 安全过滤 - 正在净化用户输入...");
            String sanitizedUserMessage = sanitizeInput(userMessage);
            String maskedMessage = sensitiveFilter.sanitize(sanitizedUserMessage);
            log.info("[步骤 1/7] 安全过滤 - 输入内容（已脱敏）: {}", maskedMessage);
            log.info("[步骤 1/7] 安全过滤 - 原始长度: {}, 净化后长度: {}",
                    userMessage != null ? userMessage.length() : 0,
                    sanitizedUserMessage != null ? sanitizedUserMessage.length() : 0);

            // 2. 构建语言指令
            log.info("[步骤 2/7] 语言指令 - 为语言: {} 构建指令...", language);
            String languageClause = buildLanguageClause(language);
            log.info("[步骤 2/7] 语言指令 - 结果: {}", languageClause.isEmpty() ? "(空)" : languageClause);

            // 3. 构建长期记忆文本（按重要性排序）
            log.info("[步骤 3/7] 长期记忆 - 正在格式化 {} 条记忆...",
                    longTermMemory != null ? longTermMemory.size() : 0);
            String longTermMemoryText = formatLongTermMemory(longTermMemory);
            log.info("[步骤 3/7] 长期记忆 - 内容:\n{}",
                    longTermMemoryText.isEmpty() ? "(空)" : longTermMemoryText);

            // 4. 动态加载系统模板（从数据库或使用默认模板）
            log.info("[步骤 4/7] 系统提示词 - 使用模板: {} 构建...", defaultTemplateName);
            String systemPrompt = buildSystemPrompt(languageClause, longTermMemoryText);
            int systemPromptTokens = calculateTokenCount(SystemMessage.from(systemPrompt));
            log.info("[步骤 4/7] 系统提示词 - Token数量: {}", systemPromptTokens);
            log.info("[步骤 4/7] 系统提示词 - 内容预览（前200字符）: {}",
                    systemPrompt.length() > 200 ? systemPrompt.substring(0, 200) + "..." : systemPrompt);
            messages.add(SystemMessage.from(systemPrompt));

            // 5. 添加对话历史
            int historyCount = shortTermMemory != null ? shortTermMemory.size() : 0;
            log.info("[步骤 5/7] 短期记忆 - 添加 {} 条历史消息...", historyCount);
            if (shortTermMemory != null) {
                messages.addAll(shortTermMemory);
                int historyTokens = calculateTokenCount(shortTermMemory);
                log.info("[步骤 5/7] 短期记忆 - Token数量: {}", historyTokens);
                for (int i = 0; i < shortTermMemory.size(); i++) {
                    ChatMessage msg = shortTermMemory.get(i);
                    String role = msg instanceof UserMessage ? "用户" : msg instanceof AiMessage ? "AI" : "系统";
                    log.debug("[步骤 5/7] 历史[{}] - {}: {}", i, role,
                            msg.text().length() > 50 ? msg.text().substring(0, 50) + "..." : msg.text());
                }
            }

            // 6. 添加用户输入
            log.info("[步骤 6/7] 用户输入 - 添加当前消息...");
            int userInputTokens = calculateTokenCount(UserMessage.from(sanitizedUserMessage));
            log.info("[步骤 6/7] 用户输入 - Token数量: {}", userInputTokens);
            messages.add(UserMessage.from(sanitizedUserMessage));

            // 7. 记录监控指标
            log.info("[步骤 7/7] 指标记录 - 正在记录...");
            if (enableMetrics) {
                recordMetrics(messages, longTermMemory, startTime, conversationId, false);
            }

            // 汇总统计
            int totalTokens = calculateTokenCount(messages);
            long duration = System.currentTimeMillis() - startTime;

            log.info("----------------------------------------");
            log.info("[Prompt组装] 汇总");
            log.info("[Prompt组装] - 会话: {}", sessionId);
            log.info("[Prompt组装] - 消息总数: {}", messages.size());
            log.info("[Prompt组装] - Token总数: {}", totalTokens);
            log.info("[Prompt组装] - 构建耗时: {}毫秒", duration);
            log.info("[Prompt组装] - 记忆项数: {}", longTermMemory != null ? longTermMemory.size() : 0);
            log.info("[Prompt组装] - 历史消息数: {}", historyCount);
            log.info("[Prompt组装] 成功");
            log.info("========================================");

            return messages;

        } catch (Exception e) {
            log.error("========================================");
            log.error("[Prompt组装] 失败 - 会话: {}, 错误: {}", sessionId, e.getMessage());
            log.error("========================================", e);
            // 降级到基础组装
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
     */
    private String buildSystemPrompt(String languageClause, String longTermMemoryText) {
        Map<String, String> params = new HashMap<>();
        params.put("language_clause", languageClause);
        params.put("long_term_memory", longTermMemoryText);

        String systemPrompt;
        try {
            // 尝试从数据库加载模板
            systemPrompt = templateService.renderTemplate(defaultTemplateName, params);
        } catch (IllegalArgumentException e) {
            log.warn("Template not found in database, using fallback template: {}", e.getMessage());
            // 使用默认硬编码模板
            systemPrompt = FALLBACK_SYSTEM_PROMPT_TEMPLATE
                    .replace("{language_clause}", languageClause)
                    .replace("{long_term_memory}", longTermMemoryText);
        }

        // 确保系统提示词不为空
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是一个智能助手。请根据上下文回答问题。";
        }

        return systemPrompt;
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
        log.warn("========================================");
        log.warn("[Prompt组装] 降级模式");
        log.warn("[Prompt组装] - 语言: {}", language);
        log.warn("[Prompt组装] - 用户输入长度: {}", userMessage != null ? userMessage.length() : 0);

        List<ChatMessage> messages = new ArrayList<>();
        String languageClause = buildLanguageClause(language);
        String systemPrompt = "你是一个智能助手。" + (languageClause.isEmpty() ? "" : languageClause);
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userMessage));

        log.warn("[Prompt组装] - 降级消息数量: {}", messages.size());
        log.warn("========================================");
        return messages;
    }

    /**
     * 向后兼容的无语言参数版本
     */
    public List<ChatMessage> assemble(
            List<ChatMessage> shortTermMemory,
            List<MemoryDTO> longTermMemory,
            String userMessage) {
        return assemble(shortTermMemory, longTermMemory, userMessage, null, null);
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

        String sessionId = conversationId != null ? conversationId : "anonymous";

        log.info("========================================");
        log.info("[Prompt截断] 开始 - 会话: {}", sessionId);
        log.info("[Prompt截断] - 最大Token限制: {}", maxTokens);

        // 先组装完整消息
        List<ChatMessage> messages = assemble(shortTermMemory, longTermMemory, userMessage, language, conversationId);
        int tokensBefore = calculateTokenCount(messages);

        log.info("[Prompt截断] - 截断前Token数: {}", tokensBefore);
        log.info("[Prompt截断] - 截断前消息数: {}", messages.size());

        // 判断是否需要截断
        if (tokensBefore <= maxTokens) {
            log.info("[Prompt截断] - 无需截断 ({} <= {})", tokensBefore, maxTokens);
            log.info("[Prompt截断] 成功 - 未截断");
            log.info("========================================");
            return messages;
        }

        // 执行截断
        List<ChatMessage> truncated = truncateToTokenLimit(messages, maxTokens);
        int tokensAfter = calculateTokenCount(truncated);

        log.info("[Prompt截断] - 截断后Token数: {}", tokensAfter);
        log.info("[Prompt截断] - 截断后消息数: {}", truncated.size());
        log.info("[Prompt截断] - 已截断: {} 个Token", tokensBefore - tokensAfter);

        // 如果发生了截断，更新指标记录
        if (enableMetrics && truncated.size() != messages.size()) {
            log.info("[Prompt截断] - 记录截断指标");
        }

        log.info("[Prompt截断] 成功");
        log.info("========================================");

        return truncated;
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
     * 格式化长期记忆为可读文本
     * 
     * 优化点：按重要性降序排序，优先展示重要的记忆
     *
     * @param memories 记忆 DTO 列表
     * @return 格式化后的字符串
     */
    private String formatLongTermMemory(List<MemoryDTO> memories) {
        if (memories == null || memories.isEmpty()) {
            return "无";
        }

        // 按重要性降序排序
        List<MemoryDTO> sortedMemories = new ArrayList<>(memories);
        sortedMemories.sort((a, b) -> Integer.compare(b.getImportance(), a.getImportance()));

        StringBuilder sb = new StringBuilder();
        for (MemoryDTO memory : sortedMemories) {
            sb.append("- [")
                    .append(memory.getType())
                    .append("] [重要性:")
                    .append(memory.getImportance())
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