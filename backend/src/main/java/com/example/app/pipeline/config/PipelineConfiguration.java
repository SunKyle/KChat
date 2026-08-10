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
 * - Agent stages check {@code ctx.isAgentMode()} and run in AGENT phase (600-699)
 *
 * AGENT_CHAT pipeline runs an agent loop via {@link ContextPipelineExecutor#executeWithAgentLoop}
 * which re-enters EXECUTION + AGENT phases until no more tool calls are produced.
 */
@Component
public class PipelineConfiguration {

    private final Map<PipelineType, List<String>> definitions = new LinkedHashMap<>();

    /**
     * Full pipeline in correct execution order (matches getOrder() values).
     * Order: 100→110→200→250→260→300→310→330→398→400→405→410→430→440→480→500→
     *        610→650→660→680→700→710→720→725→800→810→850→900→999
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
            "skillResolutionStage",             // 330 Agent: Skill 激活
            // ASSEMBLY phase
            "userProfileFormatStage",           // 398  writes agentState → read by 410
            "memoryFormatStage",                // 400  writes agentState → read by 410
            "searchContextFormatStage",         // 405  writes agentState → read by 410
            "systemPromptAssemblyStage",        // 410  reads from 400, 405; writes → read by 430
            "messageAssemblyStage",             // 430  reads from 410
            "tokenManagementStage",             // 440
            "toolDefinitionStage",              // 480 Agent: 注入 Tool 规格
            // EXECUTION phase
            "modelRoutingStage",                // 500  LLM call; streaming: triggers post-processing
            // AGENT phase (only runs when ctx.isAgentMode())
            "toolCallDetectionStage",           // 610 Agent: 检测 tool_calls
            "toolInvocationStage",              // 650 Agent: 执行 Tool
            "toolResultAssemblyStage",          // 660 Agent: 结果回填
            "agentLoopControlStage",            // 680 Agent: 循环控制
            // POSTPROCESS phase
            "shortTermMemoryUpdateStage",       // 700  adapts to streaming via userMessageInMemory flag
            "messagePersistenceStage",          // 710  adapts to streaming via userMessagePersisted flag
            "memoryExtractionStage",            // 720
            "cogneeMemoryIndexStage",           // 725
            "titleGenerationStage",             // 800
            "skillCompletionHookStage",         // 810 Agent: 状态持久化
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
