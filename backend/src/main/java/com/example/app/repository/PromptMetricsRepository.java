package com.example.app.repository;

import com.example.app.entity.PromptMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prompt 监控指标数据访问层
 */
@Repository
public interface PromptMetricsRepository extends JpaRepository<PromptMetrics, Long> {

    /**
     * 根据对话ID查询指标
     */
    List<PromptMetrics> findByConversationId(String conversationId);

    /**
     * 根据用户ID查询指标
     */
    List<PromptMetrics> findByUserId(String userId);

    /**
     * 查询指定时间范围内的指标
     */
    List<PromptMetrics> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 查询指定模型的指标
     */
    List<PromptMetrics> findByModelName(String modelName);

    /**
     * 统计指定时间范围内的平均 Token 数
     */
    @Query("SELECT AVG(p.tokenCount) FROM PromptMetrics p WHERE p.createdAt BETWEEN :start AND :end")
    Double avgTokenCount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 统计指定时间范围内的平均构建耗时
     */
    @Query("SELECT AVG(p.buildDurationMs) FROM PromptMetrics p WHERE p.createdAt BETWEEN :start AND :end")
    Double avgBuildDurationMs(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 统计指定时间范围内的截断率
     */
    @Query("SELECT COUNT(p) FROM PromptMetrics p WHERE p.truncationOccurred = true AND p.createdAt BETWEEN :start AND :end")
    Long countTruncated(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 统计指定时间范围内的总请求数
     */
    @Query("SELECT COUNT(p) FROM PromptMetrics p WHERE p.createdAt BETWEEN :start AND :end")
    Long countTotal(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 获取最近N条记录
     */
    @Query("SELECT p FROM PromptMetrics p ORDER BY p.createdAt DESC LIMIT :limit")
    List<PromptMetrics> findRecent(@Param("limit") int limit);
}