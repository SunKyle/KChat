package com.example.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内容优化请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentOptimizationRequest {

    /**
     * 需要优化的文本内容
     */
    @NotBlank(message = "内容不能为空")
    @Size(max = 4096, message = "内容长度不能超过4096字符")
    private String content;

    /**
     * 用户标识（可选）
     */
    private String userId;

    /**
     * 优化类型（可选）：grammar-语法纠错, semantic-语义优化, format-格式规范化, keyword-关键词提取
     * 默认为全部优化
     */
    private String optimizationType;

    /**
     * 模型ID（可选）：用户当前选择的模型
     */
    private String modelId;

    /**
     * 模型类型（可选）：OPENAI_COMPATIBLE, OLLAMA, OPENAI, ANTHROPIC, GOOGLE, AZURE, CUSTOM
     */
    private String modelType;

    /**
     * 模型基础URL（可选）：当使用自定义模型时需要提供
     */
    private String baseUrl;

    /**
     * API密钥（可选）：当使用需要认证的模型时需要提供
     */
    private String apiKey;
}