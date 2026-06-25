package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重新生成响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegenerateResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 新生成的消息ID
     */
    private String messageId;

    /**
     * 对话ID
     */
    private String conversationId;

    /**
     * 新的回复内容
     */
    private String content;

    /**
     * 错误代码（失败时）
     */
    private String error;

    /**
     * 错误消息（失败时）
     */
    private String message;

    /**
     * 创建成功响应
     */
    public static RegenerateResponse success(String messageId, String conversationId, String content) {
        return RegenerateResponse.builder()
                .success(true)
                .messageId(messageId)
                .conversationId(conversationId)
                .content(content)
                .build();
    }

    /**
     * 创建失败响应
     */
    public static RegenerateResponse failure(String error, String message) {
        return RegenerateResponse.builder()
                .success(false)
                .error(error)
                .message(message)
                .build();
    }
}