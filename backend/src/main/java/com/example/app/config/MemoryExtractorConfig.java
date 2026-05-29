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
}