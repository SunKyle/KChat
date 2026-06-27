package com.example.app.pipeline.config;

import com.example.app.pipeline.context.ConversationContext.PipelineType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Defines which stages compose each pipeline type.
 *
 * The same stage list serves both SIMPLE_CHAT and STREAMING_CHAT.
 * Behavioral differences are handled by:
 * - {@code isApplicable()} guards on streaming-only stages
 * - Two-phase persistence flags ({@code userMessageInMemory}, {@code userMessagePersisted})
 *   that make post-processing stages adapt to sync vs streaming.
 *
 * Phase 3: AGENT_CHAT pipeline will add tool-related stages.
 */
@Component
public class PipelineConfiguration {

    private final Map<PipelineType, List<String>> definitions = new LinkedHashMap<>();

    private static final List<String> FULL_PIPELINE = List.of(
            // Pre-processing (100-399)
            "inputSanitizationStage",
            "languageDetectionStage",
            "webSearchStage",
            "shortTermMemoryPreUpdateStage",   // streaming-only: add user msg to memory pre-LLM
            "messagePrePersistenceStage",       // streaming-only: save user msg to DB pre-LLM
            "shortTermMemoryStage",
            "longTermMemoryStage",
            // Assembly (400-499)
            "systemPromptAssemblyStage",
            "memoryFormatStage",
            "searchContextFormatStage",
            "messageAssemblyStage",
            "tokenManagementStage",
            // Execution (500-599)
            "modelRoutingStage",
            // Post-processing (700-899)
            "shortTermMemoryUpdateStage",       // adapts: sync=both, streaming=AI-only
            "messagePersistenceStage",           // adapts: sync=both, streaming=AI-only
            "memoryExtractionStage",
            "titleGenerationStage",
            "streamingDoneStage",               // streaming-only: SSE done event
            // Observability (900+)
            "metricsRecordingStage",
            "pipelineAuditStage"
    );

    public PipelineConfiguration() {
        definitions.put(PipelineType.SIMPLE_CHAT, FULL_PIPELINE);
        definitions.put(PipelineType.STREAMING_CHAT, FULL_PIPELINE);
        definitions.put(PipelineType.AGENT_CHAT, FULL_PIPELINE);
    }

    /** Get the ordered stage names for a given pipeline type. */
    public List<String> getStageNames(PipelineType type) {
        return definitions.getOrDefault(type, definitions.get(PipelineType.SIMPLE_CHAT));
    }

    /** Register or replace a pipeline composition at runtime. */
    public void registerPipeline(PipelineType type, List<String> stageNames) {
        definitions.put(type, List.copyOf(stageNames));
    }

    public Set<PipelineType> getAvailableTypes() {
        return Collections.unmodifiableSet(definitions.keySet());
    }
}
