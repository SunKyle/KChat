package com.example.app.controller;

import com.example.app.entity.PromptMetrics;
import com.example.app.service.PromptMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Prompt 监控指标控制器
 * 
 * 提供指标查询和统计 API
 */
@RestController
@RequestMapping("/api/prompt-metrics")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class PromptMetricsController {

    private final PromptMetricsService metricsService;

    /**
     * 获取统计概览（最近24小时）
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        log.info("Getting prompt metrics overview");
        Map<String, Object> overview = metricsService.getOverview();
        return ResponseEntity.ok(overview);
    }

    /**
     * 获取最近 N 条指标记录
     */
    @GetMapping("/recent")
    public ResponseEntity<List<PromptMetrics>> getRecent(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting recent {} prompt metrics", limit);
        List<PromptMetrics> metrics = metricsService.getRecent(limit);
        return ResponseEntity.ok(metrics);
    }

    /**
     * 根据对话 ID 查询指标
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<PromptMetrics>> getByConversationId(@PathVariable String conversationId) {
        log.info("Getting metrics for conversation: {}", conversationId);
        List<PromptMetrics> metrics = metricsService.getByConversationId(conversationId);
        return ResponseEntity.ok(metrics);
    }

    /**
     * 根据用户 ID 查询指标
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PromptMetrics>> getByUserId(@PathVariable String userId) {
        log.info("Getting metrics for user: {}", userId);
        List<PromptMetrics> metrics = metricsService.getByUserId(userId);
        return ResponseEntity.ok(metrics);
    }

    /**
     * 根据时间范围查询指标
     */
    @GetMapping("/range")
    public ResponseEntity<List<PromptMetrics>> getByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        log.info("Getting metrics from {} to {}", start, end);
        List<PromptMetrics> metrics = metricsService.getByTimeRange(start, end);
        return ResponseEntity.ok(metrics);
    }

    /**
     * 获取指定时间范围的统计数据
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        log.info("Getting statistics from {} to {}", start, end);
        Map<String, Object> stats = metricsService.getStatistics(start, end);
        return ResponseEntity.ok(stats);
    }
}