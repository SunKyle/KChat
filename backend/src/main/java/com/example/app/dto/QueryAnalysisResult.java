package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Query 分析结果 DTO
 *
 * <p>由 {@code QueryAnalyzer} 产出，描述用户 query 的意图分类、关键词等信息。
 * 记忆类型过滤已不再使用（JPA long_term_memory 已废弃），
 * requiredTypes / excludedTypes 保留为 String 集合以便未来扩展。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryAnalysisResult {

    /**
     * 意图类型
     */
    private IntentType intentType;

    /**
     * 关键词及其权重（1.0 = 核心关键词，0.5 = 次要关键词）
     */
    @Builder.Default
    private Map<String, Double> keywords = new LinkedHashMap<>();

    /**
     * 改写后的 query（用于向量检索，可能包含上下文扩展）
     */
    private String rewrittenQuery;

    /**
     * 需要召回的实体类型白名单（String 类型，不再依赖 LongTermMemory.MemoryType）
     */
    @Builder.Default
    private Set<String> requiredTypes = new HashSet<>();

    /**
     * 排除的实体类型黑名单
     */
    @Builder.Default
    private Set<String> excludedTypes = new HashSet<>();

    /**
     * 是否需要注入记忆（门控决策）
     */
    private boolean requiresMemory;

    /**
     * 规则判定的置信度（0~1），低于阈值时需调 LLM 深度分析
     */
    private double confidence;

    /**
     * 分析来源：RULE / LLM
     */
    @Builder.Default
    private AnalysisSource source = AnalysisSource.RULE;

    /**
     * 意图类型枚举
     */
    public enum IntentType {
        /** 知识询问（如"Java 是什么"） */
        KNOWLEDGE_QUERY,
        /** 用户档案查询（如"我叫什么名字"） */
        PROFILE_QUERY,
        /** 任务执行（如"总结这个文件"） */
        TASK_EXECUTION,
        /** 上下文依赖（如"这个文件呢"，含代词指代） */
        CONTEXT_DEPENDENT,
        /** 闲聊/问候（如"你好"） */
        CHAT_SMALLTALK,
        /** 简单数学计算 */
        MATH_CALCULATION,
        /** 通用查询 */
        GENERAL
    }

    /**
     * 分析来源枚举
     */
    public enum AnalysisSource {
        RULE,
        LLM
    }

    /**
     * 创建一个不需要记忆的结果（仅用于纯数学计算等明确场景）
     */
    public static QueryAnalysisResult skipMemory(IntentType intentType) {
        return skipMemory(intentType, AnalysisSource.RULE);
    }

    /**
     * 创建一个不需要记忆的结果，保留分析来源
     */
    public static QueryAnalysisResult skipMemory(IntentType intentType, AnalysisSource source) {
        return QueryAnalysisResult.builder()
                .intentType(intentType)
                .requiresMemory(false)
                .confidence(1.0)
                .source(source != null ? source : AnalysisSource.RULE)
                .keywords(new LinkedHashMap<>())
                .requiredTypes(new HashSet<>())
                .excludedTypes(new HashSet<>())
                .build();
    }

    /**
     * 创建一个需要记忆的通用结果
     */
    public static QueryAnalysisResult withMemory(IntentType intentType, String rewrittenQuery,
                                                  Map<String, Double> keywords,
                                                  Set<String> requiredTypes) {
        return QueryAnalysisResult.builder()
                .intentType(intentType)
                .rewrittenQuery(rewrittenQuery)
                .requiresMemory(true)
                .confidence(1.0)
                .source(AnalysisSource.RULE)
                .keywords(keywords != null ? keywords : new LinkedHashMap<>())
                .requiredTypes(requiredTypes != null ? requiredTypes : new HashSet<>())
                .excludedTypes(new HashSet<>())
                .build();
    }

    /**
     * 获取用于召回的 query（优先使用 rewrittenQuery）
     */
    public String getEffectiveQuery(String originalQuery) {
        return rewrittenQuery != null && !rewrittenQuery.isBlank() ? rewrittenQuery : originalQuery;
    }
}