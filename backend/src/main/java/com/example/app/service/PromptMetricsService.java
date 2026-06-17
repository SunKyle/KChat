package com.example.app.service;

import com.example.app.entity.PromptMetrics;
import com.example.app.repository.PromptMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt 监控指标服务
 * 
 * 提供指标收集、统计和查询功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptMetricsService {

    private final PromptMetricsRepository repository;

    /**
     * 记录 Prompt 构建指标
     * 
     * @param conversationId 对话 ID
     * @param promptVersion 模板版本
     * @param tokenCount Token 总数
     * @param memoryCount 记忆片段数量
     * @param buildDurationMs 构建耗时（毫秒）
     * @param truncationOccurred 是否发生截断
     * @param tokensBeforeTruncation 截断前 Token 数
     * @param tokensAfterTruncation 截断后 Token 数
     * @param userId 用户 ID
     * @param modelName 模型名称
     */
    @Transactional
    public void recordMetrics(
            String conversationId,
            Integer promptVersion,
            Integer tokenCount,
            Integer memoryCount,
            Long buildDurationMs,
            Boolean truncationOccurred,
            Integer tokensBeforeTruncation,
            Integer tokensAfterTruncation,
            String userId,
            String modelName) {

        PromptMetrics metrics = PromptMetrics.builder()
                .conversationId(conversationId)
                .promptVersion(promptVersion)
                .tokenCount(tokenCount)
                .memoryCount(memoryCount)
                .buildDurationMs(buildDurationMs)
                .truncationOccurred(truncationOccurred)
                .tokensBeforeTruncation(tokensBeforeTruncation)
                .tokensAfterTruncation(tokensAfterTruncation)
                .userId(userId)
                .modelName(modelName)
                .build();

        repository.save(metrics);
        log.debug("Recorded prompt metrics for conversation: {}", conversationId);
    }

    /**
     * 简化的指标记录方法
     */
    @Transactional
    public void recordMetrics(
            String conversationId,
            int tokenCount,
            int memoryCount,
            long buildDurationMs,
            boolean truncationOccurred) {
        recordMetrics(conversationId, null, tokenCount, memoryCount, buildDurationMs, truncationOccurred, null, null, null, null);
    }

    /**
     * 根据对话 ID 查询指标
     */
    @Transactional(readOnly = true)
    public List<PromptMetrics> getByConversationId(String conversationId) {
        return repository.findByConversationId(conversationId);
    }

    /**
     * 根据用户 ID 查询指标
     */
    @Transactional(readOnly = true)
    public List<PromptMetrics> getByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    /**
     * 查询指定时间范围内的指标
     */
    @Transactional(readOnly = true)
    public List<PromptMetrics> getByTimeRange(LocalDateTime start, LocalDateTime end) {
        return repository.findByCreatedAtBetween(start, end);
    }

    /**
     * 获取最近 N 条指标记录
     */
    @Transactional(readOnly = true)
    public List<PromptMetrics> getRecent(int limit) {
        return repository.findRecent(limit);
    }

    /**
     * 获取统计概览（最近24小时）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOverview() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(24);

        Map<String, Object> overview = new HashMap<>();
        
        // 平均 Token 数
        Double avgTokens = repository.avgTokenCount(start, end);
        overview.put("avgTokenCount", avgTokens != null ? avgTokens.intValue() : 0);
        
        // 平均构建耗时
        Double avgDuration = repository.avgBuildDurationMs(start, end);
        overview.put("avgBuildDurationMs", avgDuration != null ? avgDuration.longValue() : 0);
        
        // 截断次数
        Long truncatedCount = repository.countTruncated(start, end);
        overview.put("truncatedCount", truncatedCount);
        
        // 总请求数
        Long totalCount = repository.countTotal(start, end);
        overview.put("totalCount", totalCount);
        
        // 截断率
        double truncationRate = totalCount > 0 ? (truncatedCount * 100.0 / totalCount) : 0;
        overview.put("truncationRate", Math.round(truncationRate * 100) / 100.0);
        
        // 时间范围
        overview.put("startTime", start.toString());
        overview.put("endTime", end.toString());

        return overview;
    }

    /**
     * 获取指定时间范围的统计数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> stats = new HashMap<>();
        
        Double avgTokens = repository.avgTokenCount(start, end);
        stats.put("avgTokenCount", avgTokens != null ? avgTokens.intValue() : 0);
        
        Double avgDuration = repository.avgBuildDurationMs(start, end);
        stats.put("avgBuildDurationMs", avgDuration != null ? avgDuration.longValue() : 0);
        
        Long truncatedCount = repository.countTruncated(start, end);
        stats.put("truncatedCount", truncatedCount);
        
        Long totalCount = repository.countTotal(start, end);
        stats.put("totalCount", totalCount);
        
        double truncationRate = totalCount > 0 ? (truncatedCount * 100.0 / totalCount) : 0;
        stats.put("truncationRate", Math.round(truncationRate * 100) / 100.0);

        return stats;
    }
}