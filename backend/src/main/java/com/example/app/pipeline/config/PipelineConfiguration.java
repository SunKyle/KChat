package com.example.app.pipeline.config;

import com.example.app.pipeline.context.ConversationContext.PipelineType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Defines which stages compose each pipeline type.
 *
 * Currently SIMPLE_CHAT, STREAMING_CHAT, and AGENT_CHAT share the same stage list.
 * Differences are handled by per-stage {@code isApplicable()} guards:
 * - Streaming-only stages check {@code ctx.isStreaming()}
 * - Web search stage checks {@code ctx.isWebSearchEnabled()}
 * - Agent stages will check {@code ctx.getPipelineType() == AGENT_CHAT} (Phase 3)
 *
 * Phase 3 will add genuinely different stage lists for AGENT_CHAT
 * (ToolDefinitionStage, ToolCallParsingStage, ToolInvocationStage, etc.)
 */
@Component
public class PipelineConfiguration {

    private final Map<PipelineType, List<String>> definitions = new LinkedHashMap<>();

    /**
     * Full pipeline in correct execution order (matches getOrder() values).
     * Order: 100→110→200→250→260→300→310→400→405→410→430→440→500→700→710→720→800→850→900→999
     */
    private static final List<String> FULL_PIPELINE = List.of(
            // PREPROCESS phase
            "inputSanitizationStage",           // 100
            "languageDetectionStage",           // 110
            "webSearchStage",                   // 200
            "shortTermMemoryPreUpdateStage",    // 250 streaming-only
            "messagePrePersistenceStage",       // 260 streaming-only
            "shortTermMemoryStage",             // 300
            "longTermMemoryStage",              // 310
            // ASSEMBLY phase
            "memoryFormatStage",                // 400  writes agentState → read by 410
            "searchContextFormatStage",         // 405  writes agentState → read by 410
            "systemPromptAssemblyStage",        // 410  reads from 400, 405; writes → read by 430
            "messageAssemblyStage",             // 430  reads from 410
            "tokenManagementStage",             // 440
            // EXECUTION phase
            "modelRoutingStage",                // 500  LLM call; streaming: triggers post-processing
            // POSTPROCESS phase
            "shortTermMemoryUpdateStage",       // 700  adapts to streaming via userMessageInMemory flag
            "messagePersistenceStage",          // 710  adapts to streaming via userMessagePersisted flag
            "memoryExtractionStage",            // 720
            "titleGenerationStage",             // 800
            "streamingDoneStage",               // 850  streaming-only: SSE done event
            // OBSERVABILITY phase
            "metricsRecordingStage",            // 900
            "pipelineAuditStage"                // 999
    );

    public PipelineConfiguration() {
        definitions.put(PipelineType.SIMPLE_CHAT, FULL_PIPELINE);
        definitions.put(PipelineType.STREAMING_CHAT, FULL_PIPELINE);
        definitions.put(PipelineType.AGENT_CHAT, FULL_PIPELINE);
    }

    public List<String> getStageNames(PipelineType type) {
        return definitions.getOrDefault(type, definitions.get(PipelineType.SIMPLE_CHAT));
    }

    public void registerPipeline(PipelineType type, List<String> stageNames) {
        definitions.put(type, List.copyOf(stageNames));
    }

    public Set<PipelineType> getAvailableTypes() {
        return Collections.unmodifiableSet(definitions.keySet());
    }
}
