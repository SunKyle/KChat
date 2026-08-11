package com.example.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆提取服务配置
 *
 * 通过配置项控制记忆提取的触发条件和质量阈值，平衡性能与记忆质量
 */
@Configuration
@ConfigurationProperties(prefix = "memory.extractor")
@Data
public class MemoryExtractorConfig {

    /**
     * 是否启用记忆提取功能
     * 默认为 true，可在配置文件中关闭以减少 LLM 调用成本
     */
    private boolean enabled = true;

    /**
     * 消息触发阈值
     * 当对话消息数达到此值时触发记忆提取
     *
     * 设计考虑：
     * - 值过小会频繁调用 LLM 增加成本
     * - 值过大可能导致重要信息在触发前丢失
     */
    private int messageThreshold = 1;

    /**
     * 最低置信度（0-100）
     * 提取结果的置信度必须 >= 此值才会被保存
     *
     * 边界条件：
     * - 设为 0：接受所有提取结果
     * - 设为 100：只接受绝对确定的结果（实际中很难达到）
     */
    private int minConfidence = 30;

    /**
     * 最低重要性（1-10）
     * 提取结果的重要性必须 >= 此值才会被保存
     *
     * 设计决策：
     * 默认值 3 过滤掉低价值记忆，避免知识库被无关信息淹没
     */
    private int minImportance = 3;

    /**
     * 空闲超时时间（分钟）
     * 对话空闲超过此时间后，强制提取剩余记忆
     *
     * 目的：防止用户会话突然终止导致未达到阈值的记忆永远不会被提取
     */
    private long idleTimeoutMinutes = 10;

    /**
     * 是否启用自动提取
     * 关闭后只有手动调用才会触发记忆提取
     */
    private boolean autoExtractEnabled = true;

    /**
     * 上下文窗口大小
     * 每次提取时保留的历史消息数量
     *
     * 设计考虑：
     * - 过小可能丢失重要上下文信息
     * - 过大会增加 LLM 调用成本和延迟
     * - 建议值：10-30
     */
    private int contextWindowSize = 20;

    /**
     * 语义去重相似度阈值（0~1）
     *
     * 当新提取的记忆与已有记忆的向量余弦相似度 ≥ 此值时，
     * 判定为语义重复，拒绝存储。
     *
     * 设计考虑：
     * - 0.85：较严格，只有高度相似才拦截（推荐）
     * - 0.75：中等，可能误伤部分不同表述
     * - 1.0：等同精确字符串匹配
     */
    private double dedupSimilarityThreshold = 0.85;

    /**
     * 是否启用 Query 分析（意图分类 + 门控）
     *
     * 关闭后 LongTermMemoryStage 会跳过意图门控，直接按原有逻辑召回
     */
    private boolean queryAnalysisEnabled = true;

    /**
     * Query 分析是否允许调用 LLM
     *
     * 规则匹配置信度低于 llmThresholdConfidence 时，若此值为 true 则调 LLM 做深度分析
     */
    private boolean useLlm = true;

    /**
     * LLM 调用阈值置信度（0~1）
     *
     * 规则匹配置信度低于此值时，触发 LLM 深度分析
     * - 0.5：更频繁调 LLM（精度高，成本高）
     * - 0.8：仅在非常不确定时调 LLM（推荐）
     * - 1.0：完全不用 LLM
     */
    private double llmThresholdConfidence = 0.8;

    /**
     * 是否启用意图门控
     *
     * 开启后，CHAT_SMALLTALK、MATH_CALCULATION 等意图会跳过记忆注入
     */
    private boolean intentGatingEnabled = true;
}