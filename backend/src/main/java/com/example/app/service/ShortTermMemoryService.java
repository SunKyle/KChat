package com.example.app.service;

import com.example.app.memory.ShortTermMemory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 短期记忆服务，负责管理对话的短期上下文
 * 
 * <功能说明>
 * - 核心职责：维护对话的实时上下文，支持对话历史的存储和检索
 * - 设计模式：代理模式，将操作委托给底层的 ShortTermMemory 组件
 * - 依赖关系：依赖 ShortTermMemory 组件
 * 
 * <使用场景>
 * - 聊天过程中维护对话历史
 * - 为 LLM 提供上下文信息
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShortTermMemoryService {

    /**
     * 短期记忆组件，提供 L1（内存）+ L2（Redis）双层缓存
     */
    private final ShortTermMemory shortTermMemory;

    /**
     * 获取指定对话的短期记忆上下文
     * 
     * @param conversationId 对话 ID
     * @return 消息列表，按时间顺序排列
     */
    public List<ChatMessage> getMemoryContext(String conversationId) {
        log.info("[ShortTermMemoryService] Getting short-term memory for conversation: {}", conversationId);
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        List<ChatMessage> messages = memory.messages();
        log.info("[ShortTermMemoryService] Found {} short-term memory messages for conversation: {}",
                messages.size(), conversationId);
        return messages;
    }

    /**
     * 更新短期记忆，添加用户消息
     * 
     * @param conversationId 对话 ID
     * @param content 用户消息内容
     */
    public void updateMemoryWithUserMessage(String conversationId, String content) {
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        memory.add(UserMessage.from(content));
    }

    /**
     * 更新短期记忆，添加 AI 消息
     * 
     * @param conversationId 对话 ID
     * @param content AI 回复内容
     */
    public void updateMemoryWithAiMessage(String conversationId, String content) {
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        memory.add(AiMessage.from(content));
    }

    /**
     * 更新短期记忆，同时添加用户消息和 AI 回复
     * 
     * @param conversationId 对话 ID
     * @param userMessage 用户消息内容
     * @param aiMessage AI 回复内容
     */
    public void updateMemory(String conversationId, String userMessage, String aiMessage) {
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        memory.add(UserMessage.from(userMessage));
        memory.add(AiMessage.from(aiMessage));
    }

    /**
     * 清除指定对话的短期记忆
     * 
     * @param conversationId 对话 ID
     */
    public void clearMemory(String conversationId) {
        shortTermMemory.clearMemory(conversationId);
    }

    /**
     * 清除所有对话的短期记忆
     */
    public void clearAll() {
        shortTermMemory.clearAll();
    }
}
