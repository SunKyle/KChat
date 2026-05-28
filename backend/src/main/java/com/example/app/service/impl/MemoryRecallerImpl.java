package com.example.app.service.impl;

import com.example.app.dto.MemoryDTO;
import com.example.app.service.LongTermMemoryService;
import com.example.app.service.MemoryRecaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryRecallerImpl implements MemoryRecaller {

    private final LongTermMemoryService longTermMemoryService;

    /**
     * 基于语义相似度召回长期记忆片段。
     *
     * @param userId 用户唯一标识，用于隔离不同用户的记忆空间
     * @param query  当前用户输入，将作为向量检索的基准
     * @param topK   召回的最大片段数量，直接影响 Prompt 上下文长度
     * @return 相关记忆列表。若检索服务异常则返回空列表，保证对话链路不被中断（降级处理）。
     */
    @Override
    public List<MemoryDTO> recall(String userId, String query, int topK) {
        try {
            return longTermMemoryService.recall(userId, query, topK);
        } catch (Exception e) {
            log.warn("Long-term memory recall failed, falling back to short-term only: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<MemoryDTO> recall(String userId, String query, int topK, List<String> types) {
        if (types == null || types.isEmpty()) {
            return recall(userId, query, topK);
        }

        try {
            return longTermMemoryService.recall(userId, query, topK, types);
        } catch (Exception e) {
            log.warn("Filtered memory recall failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取用户的高权重/核心记忆。
     * 适用于系统初始化或全局背景设定，无需基于 Query 检索。
     */
    public List<MemoryDTO> getHighPriorityMemories(String userId) {
        return longTermMemoryService.findByUserIdAndMinImportance(userId, 7);
    }
}
