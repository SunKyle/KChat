package com.example.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重新生成请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegenerateRequest {

    /**
     * 对话ID
     */
    @NotBlank(message = "对话ID不能为空")
    private String conversationId;

    /**
     * 要重新生成的消息ID（AI消息）
     */
    @NotBlank(message = "消息ID不能为空")
    private String messageId;

    /**
     * 用户ID（可选，默认使用 "default"）
     */
    private String userId;

    /**
     * 模型名称（可选，默认使用 "llama3"）
     */
    private String model;
}