package com.example.app.pipeline.context;

import com.example.app.dto.MemoryDTO;
import com.example.app.dto.QueryAnalysisResult;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 记忆上下文，包括短期记忆和 Cognee 知识图谱长期记忆。
 *
 * <p>{@code cogneeContext} 字段保留为 {@code Object} 类型，由 Pipeline 边界
 * （{@code LongTermMemoryStage}）注入具体中性载体 {@link CogneeMemoryContext}，
 * 避免上下文层依赖具体服务实现（如 {@code CogneeClient}）。
 */
@Data
@Builder(toBuilder = true)
public class MemoryContext {
    private List<ChatMessage> shortTermMemory;
    private QueryAnalysisResult queryAnalysisResult;

    /**
     * Cognee 知识图谱上下文（片段 + 实体 + 关系）。
     * 由 LongTermMemoryStage(310) 写入，MemoryFormatStage(400) 读取。
     * 实际运行时类型为 {@link CogneeMemoryContext}。
     */
    private Object cogneeContext;

    /**
     * Memories newly extracted by MemoryExtractionStage (this run), for downstream
     * stages like Cognee indexing.
     */
    private List<MemoryDTO> newlyExtractedMemories;

    /** Cognee 上下文快捷获取（需要转型为 CogneeMemoryContext） */
    @SuppressWarnings("unchecked")
    public <T> T getTypedCogneeContext() {
        if (cogneeContext == null) return null;
        return (T) cogneeContext;
    }

    public boolean hasCogneeContext() {
        return cogneeContext != null;
    }
}