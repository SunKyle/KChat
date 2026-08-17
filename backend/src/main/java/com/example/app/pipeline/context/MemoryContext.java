package com.example.app.pipeline.context;

import com.example.app.dto.MemoryDTO;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.service.CogneeClient;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 记忆上下文，包括短期记忆和 Cognee 知识图谱长期记忆。
 */
@Data
@Builder(toBuilder = true)
public class MemoryContext {
    private List<ChatMessage> shortTermMemory;
    private QueryAnalysisResult queryAnalysisResult;

    /**
     * Cognee 知识图谱上下文（片段 + 实体 + 关系）。
     * 由 LongTermMemoryStage(310) 写入，MemoryFormatStage(400) 读取。
     */
    private Object cogneeContext;

    /**
     * Memories newly extracted by MemoryExtractionStage (this run), for downstream
     * stages like Cognee indexing.
     */
    private List<MemoryDTO> newlyExtractedMemories;

    /** Cognee 上下文快捷获取（需要转型为 CogneeContext） */
    @SuppressWarnings("unchecked")
    public <T> T getTypedCogneeContext() {
        if (cogneeContext == null) return null;
        return (T) cogneeContext;
    }

    public boolean hasCogneeContext() {
        return cogneeContext != null;
    }

    public record CogneeContext(
            List<CogneeClient.RecallResult> fragments,
            List<String> entities,
            List<CogneeClient.CogneeRelationRecord> relations) {
        public boolean isEmpty() {
            return (fragments == null || fragments.isEmpty())
                    && (entities == null || entities.isEmpty())
                    && (relations == null || relations.isEmpty());
        }
    }
}