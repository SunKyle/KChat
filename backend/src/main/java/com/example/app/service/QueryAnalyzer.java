package com.example.app.service;

import com.example.app.config.MemoryExtractorConfig;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.dto.QueryAnalysisResult.AnalysisSource;
import com.example.app.dto.QueryAnalysisResult.IntentType;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.service.ai.AiServiceFactory;
import com.example.app.service.ai.QueryAnalysisAI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Query 分析服务
 *
 * <p>
 * 采用规则 + LLM 混合方案：
 * <ol>
 * <li>规则优先（零成本）：匹配代词、问候、技术关键词等模式</li>
 * <li>规则不确定时调 LLM：产出结构化的意图分类 + query 改写</li>
 * </ol>
 *
 * <p>
 * 分析结果供 {@code LongTermMemoryStage} 使用，提供：
 * <ul>
 * <li>意图分类（用于决定优先召回哪些类型的记忆）</li>
 * <li>召回 query 改写（用于向量检索）</li>
 * <li>类型过滤建议（requiredTypes / excludedTypes）</li>
 * </ul>
 *
 * <p>
 * 设计原则：意图分类仅用于排序/过滤，不用于门控。
 * LongTermMemoryStage 默认始终召回记忆，只有纯数学计算才跳过。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAnalyzer {

    private final AiServiceFactory aiServiceFactory;
    private final MemoryExtractorConfig config;

    // ── 规则匹配模式 ──────────────────────────────────────────

    /** 代词/指代词 → CONTEXT_DEPENDENT */
    private static final Pattern CONTEXT_PRONOUN_PATTERN = Pattern.compile(
            "(这个|那个|刚才|之前|上文|上面|前述|刚才说的|之前提到的|it|that|this)",
            Pattern.CASE_INSENSITIVE);

    /** 身份询问 → PROFILE_QUERY */
    private static final Pattern PROFILE_PATTERN = Pattern.compile(
            "(你叫|我叫|我是|我的名字|我的昵称|我的名字是|你是谁|who are you|what is my name)",
            Pattern.CASE_INSENSITIVE);

    /** 技术/知识询问 → KNOWLEDGE_QUERY */
    private static final Pattern KNOWLEDGE_PATTERN = Pattern.compile(
            "(技术栈|用什么框架|怎么实现|如何做|代码|开发|编程|是什么|是什么意思|介绍一下|解释一下" +
                    "|知道|了解|认识|熟悉|听过|了解过|有没有|会不会|能否|能不能" +
                    "|framework|technology|how to|how does|what is|explain|describe|implement" +
                    "|know|learn|understand|familiar|heard)",
            Pattern.CASE_INSENSITIVE);

    /** 任务执行 → TASK_EXECUTION */
    private static final Pattern TASK_PATTERN = Pattern.compile(
            "(总结|翻译|处理|生成|创建|删除|提取|转换|分析|优化|修改|编辑" +
                    "|summar|translat|process|generat|creat|delet|extract|convert|analyz|optimiz|modif|edit)",
            Pattern.CASE_INSENSITIVE);

    /** 问候 → CHAT_SMALLTALK */
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^\\s*(你好|hi|hello|早上好|晚安|在吗|您好|hey|greetings|good (morning|afternoon|evening))\\s*[!！。.？?]*$",
            Pattern.CASE_INSENSITIVE);

    /** 闲聊 → CHAT_SMALLTALK */
    private static final Pattern SMALLTALK_PATTERN = Pattern.compile(
            "(最近怎么样|在吗|聊聊天|你好吗|好久不见|what's up|how are you|how have you been)",
            Pattern.CASE_INSENSITIVE);

    /** 数学计算 → MATH_CALCULATION */
    private static final Pattern MATH_PATTERN = Pattern.compile(
            "^\\s*\\d+\\s*[\\+\\-\\*\\/×÷]\\s*\\d+\\s*=?\\s*\\??\\s*$");

    /** 文件相关 → TASK_EXECUTION */
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "(文件|文档|图片|照片|PDF|docx|excel|word|file|document|image|photo|pdf)",
            Pattern.CASE_INSENSITIVE);

    // ── 意图→记忆类型映射 ──────────────────────────────────────

    private static final Map<IntentType, Set<MemoryType>> INTENT_TO_REQUIRED_TYPES = new EnumMap<>(IntentType.class);
    private static final Map<IntentType, Set<MemoryType>> INTENT_TO_EXCLUDED_TYPES = new EnumMap<>(IntentType.class);
    private static final Set<MemoryType> ALL_TYPES = EnumSet.allOf(MemoryType.class);

    static {
        // KNOWLEDGE_QUERY: 技术/知识相关
        INTENT_TO_REQUIRED_TYPES.put(IntentType.KNOWLEDGE_QUERY,
                EnumSet.of(MemoryType.SKILL, MemoryType.KNOWLEDGE, MemoryType.PROJECT));
        INTENT_TO_EXCLUDED_TYPES.put(IntentType.KNOWLEDGE_QUERY,
                EnumSet.of(MemoryType.PREFERENCE, MemoryType.RELATION, MemoryType.EVENT));

        // PROFILE_QUERY: 用户档案
        INTENT_TO_REQUIRED_TYPES.put(IntentType.PROFILE_QUERY,
                EnumSet.of(MemoryType.PROFILE, MemoryType.PREFERENCE));
        INTENT_TO_EXCLUDED_TYPES.put(IntentType.PROFILE_QUERY,
                EnumSet.complementOf(EnumSet.of(MemoryType.PROFILE, MemoryType.PREFERENCE)));

        // TASK_EXECUTION: 任务/项目
        INTENT_TO_REQUIRED_TYPES.put(IntentType.TASK_EXECUTION,
                EnumSet.of(MemoryType.TASK, MemoryType.PROJECT, MemoryType.EVENT));
        INTENT_TO_EXCLUDED_TYPES.put(IntentType.TASK_EXECUTION,
                EnumSet.of(MemoryType.PROFILE, MemoryType.PREFERENCE, MemoryType.KNOWLEDGE));

        // CHAT_SMALLTALK: 仅昵称
        INTENT_TO_REQUIRED_TYPES.put(IntentType.CHAT_SMALLTALK,
                EnumSet.of(MemoryType.PROFILE));
        INTENT_TO_EXCLUDED_TYPES.put(IntentType.CHAT_SMALLTALK,
                EnumSet.complementOf(EnumSet.of(MemoryType.PROFILE)));

        // CONTEXT_DEPENDENT: 全部类型
        INTENT_TO_REQUIRED_TYPES.put(IntentType.CONTEXT_DEPENDENT, ALL_TYPES);

        // GENERAL: 全部类型
        INTENT_TO_REQUIRED_TYPES.put(IntentType.GENERAL, ALL_TYPES);
    }

    // ── 主入口 ──────────────────────────────────────────────────

    /**
     * 分析用户 query，产出结构化的召回计划
     *
     * @param userMessage 用户原始输入
     * @param modelId     用于 LLM 分析的模型 ID（可为空，空时用默认模型）
     * @return Query 分析结果
     */
    public QueryAnalysisResult analyze(String userMessage, String modelId) {
        if (userMessage == null || userMessage.isBlank()) {
            return QueryAnalysisResult.skipMemory(IntentType.GENERAL);
        }

        // 1. 规则匹配
        RuleMatch ruleMatch = matchRules(userMessage);

        // 2. 规则置信度足够 → 直接返回
        if (ruleMatch.confidence >= config.getLlmThresholdConfidence()) {
            return buildResultFromRule(userMessage, ruleMatch);
        }

        // 3. 规则不确定且允许调 LLM → 调 LLM
        if (config.isQueryAnalysisEnabled() && config.isUseLlm()) {
            try {
                QueryAnalysisResult llmResult = analyzeWithLlm(userMessage, modelId);
                if (llmResult != null) {
                    log.debug("[QueryAnalyzer] LLM analysis: intent={}, requiresMemory={}",
                            llmResult.getIntentType(), llmResult.isRequiresMemory());
                    return llmResult;
                }
            } catch (Exception e) {
                log.warn("[QueryAnalyzer] LLM analysis failed, falling back to rules: {}",
                        e.getMessage());
            }
        }

        // 4. 降级：用规则结果
        log.debug("[QueryAnalyzer] Rule-based analysis: intent={}, confidence={}",
                ruleMatch.intentType, ruleMatch.confidence);
        return buildResultFromRule(userMessage, ruleMatch);
    }

    /**
     * 简化入口：不传 modelId
     */
    public QueryAnalysisResult analyze(String userMessage) {
        return analyze(userMessage, null);
    }

    // ── 规则匹配 ──────────────────────────────────────────────

    private RuleMatch matchRules(String query) {
        String q = query.trim();

        // 数学计算 → 直接跳过
        if (MATH_PATTERN.matcher(q).matches()) {
            return new RuleMatch(IntentType.MATH_CALCULATION, 1.0, new LinkedHashMap<>());
        }

        // 问候 → 跳过
        if (GREETING_PATTERN.matcher(q).matches()) {
            return new RuleMatch(IntentType.CHAT_SMALLTALK, 1.0, new LinkedHashMap<>());
        }

        // 闲聊 → 跳过
        if (SMALLTALK_PATTERN.matcher(q).find()) {
            return new RuleMatch(IntentType.CHAT_SMALLTALK, 0.9, new LinkedHashMap<>());
        }

        // 代词指代 → CONTEXT_DEPENDENT
        if (CONTEXT_PRONOUN_PATTERN.matcher(q).find()) {
            Map<String, Double> keywords = extractKeywords(q);
            return new RuleMatch(IntentType.CONTEXT_DEPENDENT, 0.85, keywords);
        }

        // 身份询问 → PROFILE_QUERY
        if (PROFILE_PATTERN.matcher(q).find()) {
            Map<String, Double> keywords = extractKeywords(q);
            return new RuleMatch(IntentType.PROFILE_QUERY, 0.9, keywords);
        }

        // 任务执行（文件操作类）
        if (TASK_PATTERN.matcher(q).find() && FILE_PATTERN.matcher(q).find()) {
            Map<String, Double> keywords = extractKeywords(q);
            return new RuleMatch(IntentType.TASK_EXECUTION, 0.85, keywords);
        }

        // 技术/知识询问
        if (KNOWLEDGE_PATTERN.matcher(q).find()) {
            Map<String, Double> keywords = extractKeywords(q);
            return new RuleMatch(IntentType.KNOWLEDGE_QUERY, 0.75, keywords);
        }

        // 任务执行
        if (TASK_PATTERN.matcher(q).find()) {
            Map<String, Double> keywords = extractKeywords(q);
            return new RuleMatch(IntentType.TASK_EXECUTION, 0.7, keywords);
        }

        // 文件相关（无明确任务词）
        if (FILE_PATTERN.matcher(q).find()) {
            Map<String, Double> keywords = extractKeywords(q);
            return new RuleMatch(IntentType.TASK_EXECUTION, 0.55, keywords);
        }

        // 通用 → 低置信度，可能需要 LLM
        Map<String, Double> keywords = extractKeywords(q);
        return new RuleMatch(IntentType.GENERAL, 0.4, keywords);
    }

    /**
     * 从 query 中提取关键词（简单分词 + 停用词过滤）
     */
    private Map<String, Double> extractKeywords(String query) {
        Map<String, Double> keywords = new LinkedHashMap<>();
        String[] words = query.split("[\\s,，。？?!！；;：:、]+");

        Set<String> stopWords = Set.of("的", "了", "是", "在", "我", "你", "他", "她", "它",
                "这", "那", "个", "和", "与", "或", "不", "也", "都", "就",
                "the", "a", "an", "is", "are", "was", "were", "do", "does",
                "did", "have", "has", "had", "can", "could", "should", "would");

        for (String word : words) {
            String w = word.trim();
            if (w.length() < 2 || stopWords.contains(w.toLowerCase())) {
                continue;
            }
            // 完整匹配的词权重 1.0，子串匹配的权重 0.5
            double weight = keywords.isEmpty() ? 1.0 : 0.8;
            keywords.put(w, weight);
        }

        return keywords;
    }

    // ── 规则→结果构建 ──────────────────────────────────────────

    private QueryAnalysisResult buildResultFromRule(String originalQuery, RuleMatch match) {
        IntentType intentType = match.intentType;

        // 只有 MATH_CALCULATION 设为不需要记忆（LongTermMemoryStage 也会做二次校验）
        if (intentType == IntentType.MATH_CALCULATION) {
            return QueryAnalysisResult.skipMemory(intentType);
        }

        // 构建改写 query
        String rewrittenQuery = buildRewrittenQuery(originalQuery, match);

        // 获取类型映射
        Set<MemoryType> requiredTypes = INTENT_TO_REQUIRED_TYPES.getOrDefault(
                intentType, ALL_TYPES);
        Set<MemoryType> excludedTypes = INTENT_TO_EXCLUDED_TYPES.getOrDefault(
                intentType, EnumSet.noneOf(MemoryType.class));

        return QueryAnalysisResult.builder()
                .intentType(intentType)
                .keywords(match.keywords)
                .rewrittenQuery(rewrittenQuery)
                .requiresMemory(true)
                .confidence(match.confidence)
                .source(AnalysisSource.RULE)
                .requiredTypes(EnumSet.copyOf(requiredTypes))
                .excludedTypes(EnumSet.copyOf(excludedTypes))
                .build();
    }

    /**
     * 构建改写后的 query：在原 query 基础上追加意图相关的扩展词
     */
    private String buildRewrittenQuery(String originalQuery, RuleMatch match) {
        StringBuilder sb = new StringBuilder(originalQuery);

        switch (match.intentType) {
            case KNOWLEDGE_QUERY -> {
                // 追加技术/知识上下文
                if (originalQuery.length() < 30) {
                    sb.append(" 技术栈 编程语言 框架实现");
                }
            }
            case TASK_EXECUTION -> {
                // 追加任务上下文
                if (originalQuery.length() < 30) {
                    sb.append(" 相关项目 文件处理");
                }
            }
            case PROFILE_QUERY -> {
                // 追加用户档案上下文
                if (originalQuery.length() < 30) {
                    sb.append(" 用户信息 身份 偏好");
                }
            }
            case CONTEXT_DEPENDENT -> {
                // 保持原样，依赖向量检索的上下文匹配能力
            }
            default -> {
                // 通用：保持原样
            }
        }

        return sb.toString();
    }

    // ── LLM 分析 ──────────────────────────────────────────────

    private QueryAnalysisResult analyzeWithLlm(String query, String modelId) {
        QueryAnalysisAI ai = aiServiceFactory.create(QueryAnalysisAI.class, modelId);
        QueryAnalysisAI.QueryAnalysisResultDTO dto = ai.analyze(query);

        if (dto == null || dto.intentType() == null) {
            return null;
        }

        IntentType intentType;
        try {
            intentType = IntentType.valueOf(dto.intentType().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[QueryAnalyzer] Unknown intent type from LLM: {}", dto.intentType());
            intentType = IntentType.GENERAL;
        }

        // 只有 MATH_CALCULATION 是硬跳过，其他意图始终召回记忆
        if (intentType == IntentType.MATH_CALCULATION) {
            return QueryAnalysisResult.skipMemory(intentType, AnalysisSource.LLM);
        }

        Map<String, Double> keywords = dto.keywords() != null ? dto.keywords() : new LinkedHashMap<>();
        String rewrittenQuery = dto.rewrittenQuery() != null ? dto.rewrittenQuery() : query;

        Set<MemoryType> requiredTypes = INTENT_TO_REQUIRED_TYPES.getOrDefault(
                intentType, ALL_TYPES);
        Set<MemoryType> excludedTypes = INTENT_TO_EXCLUDED_TYPES.getOrDefault(
                intentType, EnumSet.noneOf(MemoryType.class));

        return QueryAnalysisResult.builder()
                .intentType(intentType)
                .keywords(keywords)
                .rewrittenQuery(rewrittenQuery)
                .requiresMemory(true)
                .confidence(0.9)
                .source(AnalysisSource.LLM)
                .requiredTypes(EnumSet.copyOf(requiredTypes))
                .excludedTypes(EnumSet.copyOf(excludedTypes))
                .build();
    }

    // ── 内部数据类 ──────────────────────────────────────────

    private record RuleMatch(IntentType intentType, double confidence, Map<String, Double> keywords) {
    }
}