package com.example.app.util;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * Token 估算器接口
 * 
 * 提供消息的 Token 数量估算能力，支持多种估算策略
 */
public interface TokenEstimator {

    /**
     * 估算单个消息的 Token 数量
     * 
     * @param message 消息内容
     * @return Token 数量
     */
    int estimate(ChatMessage message);

    /**
     * 估算消息列表的总 Token 数量
     * 
     * @param messages 消息列表
     * @return 总 Token 数量
     */
    int estimate(List<ChatMessage> messages);

    /**
     * 估算文本字符串的 Token 数量
     * 
     * @param text 文本内容
     * @return Token 数量
     */
    int estimateText(String text);

    /**
     * 获取当前使用的编码类型
     * 
     * @return 编码类型名称
     */
    String getEncodingType();
}