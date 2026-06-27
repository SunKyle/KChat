package com.example.app.pipeline.config;

import com.example.app.pipeline.context.ConversationContext.PipelineType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Defines which stages compose each pipeline type.
 *
 * Each pipeline type maps to an ordered list of stage names. The executor
 * uses this configuration to resolve which stages to run and in what order.
 * Stage isApplicable() guards determine runtime skip vs. execute.
 *
 * Phase 2: Full production stages for SIMPLE_CHAT and STREAMING_CHAT.
 * Phase 3: AGENT_CHAT pipeline will be added.
 */
@Component
public class PipelineConfiguration {

    private final Map<PipelineType, List<String>> definitions = new LinkedHashMap<>();

    public PipelineConfiguration() {
        definitions.put(PipelineType.SIMPLE_CHAT, List.of(
                "inputSanitizationStage",
                "languageDetectionStage",
                "webSearchStage",
                "shortTermMemoryStage",
                "longTermMemoryStage",
                "systemPromptAssemblyStage",
                "memoryFormatStage",
                "searchContextFormatStage",
                "messageAssemblyStage",
                "tokenManagementStage",
                "modelRoutingStage",
                "shortTermMemoryUpdateStage",
                "messagePersistenceStage",
                "memoryExtractionStage",
                "titleGenerationStage",
                "metricsRecordingStage",
                "pipelineAuditStage"
        ));

        definitions.put(PipelineType.STREAMING_CHAT, definitions.get(PipelineType.SIMPLE_CHAT));

        definitions.put(PipelineType.AGENT_CHAT, definitions.get(PipelineType.SIMPLE_CHAT));
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
