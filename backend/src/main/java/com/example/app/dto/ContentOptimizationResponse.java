package com.example.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 内容优化响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentOptimizationResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 优化后的文本内容
     */
    private String optimizedContent;

    /**
     * 原始文本内容
     */
    private String originalContent;

    /**
     * 优化详情列表
     */
    private List<OptimizationDetail> optimizations;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTimeMs;

    /**
     * 错误码（失败时）
     */
    private String error;

    /**
     * 错误消息（失败时）
     */
    private String message;

    /**
     * 重试等待时间（秒）（限流时）
     */
    private Integer retryAfterSeconds;

    /**
     * 优化详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationDetail {
        /**
         * 优化类型：grammar, semantic, format, keyword
         */
        private String type;

        /**
         * 优化描述
         */
        private String description;
    }

    /**
     * 创建成功响应
     */
    public static ContentOptimizationResponse success(String optimizedContent, String originalContent,
                                                      List<OptimizationDetail> optimizations, long processingTimeMs) {
        return ContentOptimizationResponse.builder()
                .success(true)
                .optimizedContent(optimizedContent)
                .originalContent(originalContent)
                .optimizations(optimizations)
                .processingTimeMs(processingTimeMs)
                .build();
    }

    /**
     * 创建失败响应
     */
    public static ContentOptimizationResponse failure(String error, String message) {
        return ContentOptimizationResponse.builder()
                .success(false)
                .error(error)
                .message(message)
                .build();
    }

    /**
     * 创建限流响应
     */
    public static ContentOptimizationResponse rateLimitExceeded(int retryAfterSeconds) {
        return ContentOptimizationResponse.builder()
                .success(false)
                .error("RATE_LIMIT_EXCEEDED")
                .message("请求过于频繁，请稍后重试")
                .retryAfterSeconds(retryAfterSeconds)
                .build();
    }
}