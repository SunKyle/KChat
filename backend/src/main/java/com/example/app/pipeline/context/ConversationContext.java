package com.example.app.pipeline.context;

import com.example.app.dto.Artifact;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.MemoryDTO;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.dto.WebSearchResult;
import com.example.app.entity.ModelConfig;
import com.example.app.service.CogneeClient;
import com.example.app.util.JsonUtils;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Rich context object carrying all state through the pipeline.
 *
 * Replaces the scattered parameter passing pattern where services passed
 * (shortTermMemory, longTermMemory, userMessage, language, conversationId,
 * searchContext)
 * as individual arguments. All pipeline stages read from and write to this
 * single context.
 *
 * Design constraints:
 * - Mutable by design — stages modify it in-place during execution
 * - Builder pattern for initial construction from ChatRequest
 * - Stage-specific sections use namespaced groups to avoid collisions
 * - Thread-safe for the lifetime of a single request (not shared across
 * requests)
 */
@Data
@Builder(toBuilder = true)
public class ConversationContext {

    // ── Request metadata ───────────────────────────────────────────
    private String conversationId;
    private String userId;
    private String userMessage;
    private String model;
    private List<String> imageUrls;
    private boolean webSearchEnabled;

    // ── Pipeline orchestration ─────────────────────────────────────
    private PipelineType pipelineType;
    private int maxAgentIterations;
    private int currentIteration;
    private final List<String> executedStageNames = new ArrayList<>();

    // ── Memory ─────────────────────────────────────────────────────
    private List<ChatMessage> shortTermMemory;
    private List<MemoryDTO> longTermMemory;
    private QueryAnalysisResult queryAnalysisResult;

    /**
     * JPA 结构化记忆，按层级分组：
     * "l1" = 用户档案 (PROFILE), "l2" = 相关记忆 (FACT/KNOWLEDGE), "l3" = 用户偏好
     * (PREFERENCE/SKILL)
     * 由 LongTermMemoryStage(310) 写入，MemoryFormatStage(400) 读取。
     */
    private Map<String, List<MemoryDTO>> jpaMemories;

    /**
     * Cognee 知识图谱上下文（片段 + 实体 + 关系）。
     * 由 LongTermMemoryStage(310) 写入，MemoryFormatStage(400) 读取。
     */
    private Object cogneeContext;

    // ── Context enrichment ─────────────────────────────────────────
    private String language;
    private String searchContext;
    private WebSearchResult rawSearchResult;
    private String customRules;

    // ── Agent / Skill ─────────────────────────────────────────────
    private boolean agentMode;
    private String activeSkillId;
    private List<Artifact> artifacts;

    // ── Assembly state ─────────────────────────────────────────────
    private List<ChatMessage> assembledMessages;
    private int tokenCount;
    private boolean truncated;
    private String aiMessageId;

    // ── LLM execution ──────────────────────────────────────────────
    private ModelConfig customModelConfig;
    private String llmResponse;
    private boolean streaming;
    private Object sseEmitter;

    // ── Streaming: two-phase persistence tracking ─────────────────
    private boolean userMessagePersisted;
    private boolean userMessageInMemory;

    // ── Agent / tool state ────────────────────────────────────────
    private final List<ToolCallRecord> toolCalls = new ArrayList<>();
    private final List<ToolResultRecord> toolResults = new ArrayList<>();
    private final List<String> enabledToolNames = new ArrayList<>();
    private final Map<String, Object> agentState = new HashMap<>();
    private final List<Map<String, Object>> agentThinkingSteps = new ArrayList<>();

    // ── Post-processing outputs ────────────────────────────────────
    private String generatedTitle;
    /**
     * Memories newly extracted by MemoryExtractionStage (this run), for downstream
     * stages like Cognee indexing
     */
    private List<MemoryDTO> newlyExtractedMemories;

    // ── Telemetry ──────────────────────────────────────────────────
    private long pipelineStartTime;
    private final Map<String, Long> stageTimings = new LinkedHashMap<>();
    private final PipelineTrace trace = new PipelineTrace();

    // ── Error handling ─────────────────────────────────────────────
    private final List<PipelineError> errors = new ArrayList<>();

    // ── Factory ────────────────────────────────────────────────────

    public static ConversationContext fromRequest(ChatRequest request) {
        ConversationContext ctx = ConversationContext.builder()
                .conversationId(request.getConversationId())
                .userId(request.getUserId() != null ? request.getUserId() : "default")
                .userMessage(request.getMessage())
                .model(request.getModel())
                .imageUrls(request.getImageUrls() != null ? request.getImageUrls() : List.of())
                .webSearchEnabled(request.isWebSearch())
                .pipelineType(PipelineType.SIMPLE_CHAT)
                .maxAgentIterations(5)
                .currentIteration(0)
                .pipelineStartTime(System.currentTimeMillis())
                .build();
        ctx.getTrace().setStartTime(ctx.getPipelineStartTime());
        return ctx;
    }

    // ── Well-known agentState keys (shared between assembly stages) ─

    /**
     * Key for formatted long-term memory text, written by MemoryFormatStage(400),
     * read by SystemPromptAssemblyStage(410)
     */
    public static final String KEY_FORMATTED_MEMORY = "formattedLongTermMemory";
    /**
     * Key for formatted L1 user profile memory (always injected),
     * written by MemoryFormatStage(400), read by SystemPromptAssemblyStage(410)
     */
    public static final String KEY_FORMATTED_MEMORY_L1 = "formattedMemoryL1Profile";
    /**
     * Key for formatted L2 query-relevant memory (dynamically injected),
     * written by MemoryFormatStage(400), read by SystemPromptAssemblyStage(410)
     */
    public static final String KEY_FORMATTED_MEMORY_L2 = "formattedMemoryL2Relevant";
    /**
     * Key for formatted L3 user preference memory (optionally injected),
     * written by MemoryFormatStage(400), read by SystemPromptAssemblyStage(410)
     */
    public static final String KEY_FORMATTED_MEMORY_L3 = "formattedMemoryL3Preference";
    /**
     * Key for formatted Cognee knowledge graph context (entities + relations +
     * fragments),
     * written by MemoryFormatStage(400), read by SystemPromptAssemblyStage(410)
     */
    public static final String KEY_FORMATTED_MEMORY_COGNEE = "formattedMemoryCogneeGraph";
    /**
     * Key for formatted precise JPA memory (FACT/KNOWLEDGE type, exact matches),
     * written by MemoryFormatStage(400), read by SystemPromptAssemblyStage(410)
     */
    public static final String KEY_FORMATTED_MEMORY_PRECISE = "formattedMemoryPrecise";
    /**
     * Key for formatted user profile text, written by UserProfileFormatStage(398),
     * read by SystemPromptAssemblyStage(410)
     */
    public static final String KEY_FORMATTED_USER_PROFILE = "formattedUserProfile";
    /**
     * Key for formatted search context section, written by
     * SearchContextFormatStage(405), read by SystemPromptAssemblyStage(410)
     */
    public static final String KEY_FORMATTED_SEARCH = "formattedSearchContext";
    /**
     * Key for the assembled SystemMessage, written by
     * SystemPromptAssemblyStage(410), read by MessageAssemblyStage(430)
     */
    public static final String KEY_SYSTEM_MESSAGE = "assembledSystemMessage";
    /**
     * Key for the active system prompt template version, written by
     * SystemPromptAssemblyStage(410), read by ModelRoutingStage(500)
     */
    public static final String KEY_PROMPT_TEMPLATE_VERSION = "promptTemplateVersion";
    /**
     * Key for the last AiMessage from ModelRoutingStage in Agent mode, read by
     * ToolCallDetectionStage(610) and ToolResultAssemblyStage(660)
     */
    public static final String KEY_LAST_AI_MESSAGE = "lastAiMessage";

    // ── Convenience ────────────────────────────────────────────────

    /** JPA 记忆快捷获取：l1=用户档案, l2=相关记忆, l3=用户偏好 */
    public List<MemoryDTO> getJpaMemoriesByLayer(String layer) {
        if (jpaMemories == null)
            return List.of();
        return jpaMemories.getOrDefault(layer, List.of());
    }

    public void setJpaMemories(Map<String, List<MemoryDTO>> memories) {
        this.jpaMemories = memories;
    }

    /** Cognee 上下文快捷获取（需要转型为 CogneeContext） */
    public record CogneeContext(
            List<CogneeClient.RecallResult> fragments,
            List<String> entities,
            List<CogneeRelation> relations) {
        public boolean isEmpty() {
            return (fragments == null || fragments.isEmpty())
                    && (entities == null || entities.isEmpty())
                    && (relations == null || relations.isEmpty());
        }
    }

    public record CogneeRelation(String source, String relation, String target) {
    }

    public CogneeContext getCogneeContextTyped() {
        if (cogneeContext instanceof CogneeContext ctx)
            return ctx;
        return null;
    }

    public void setCogneeContext(CogneeContext ctx) {
        this.cogneeContext = ctx;
    }

    public void recordStage(String name, long durationMs) {
        executedStageNames.add(name);
        stageTimings.put(name, durationMs);
    }

    public void addError(String stageName, String message, Throwable cause, boolean recoverable) {
        errors.add(new PipelineError(stageName, message, cause, recoverable));
    }

    public boolean hasErrors() {
        return errors.stream().anyMatch(e -> !e.recoverable());
    }

    /**
     * Emit an SSE event if this is a streaming context and an emitter is present.
     * Silently no-ops for non-streaming contexts.
     */
    public void emitSseEvent(String eventName, String jsonData) {
        if (!streaming || sseEmitter == null)
            return;
        try {
            SseEmitter emitter = (SseEmitter) sseEmitter;
            emitter.send(SseEmitter.event().name(eventName).data(jsonData));
        } catch (Exception e) {
            // emitter may already be closed/completed — don't disrupt the pipeline
        }
    }

    /**
     * 推送 Agent 思考过程事件（agent_thinking）。
     *
     * <p>封装统一的 envelope 结构：
     * <pre>{@code
     * {
     * "type": "tool_definition" | "llm_call" | "tool_detection" | "tool_execution"
     * | "tool_assembly" | "final_response",
     * "iteration": <当前 Agent 循环轮次>,
     * "timestamp": <毫秒时间戳>,
     * "data": <各类型自定义负载>
     * }
     * }</pre>
     *
     * <p>仅在流式上下文推送（emitSseEvent 内置判空），同步 ChatService 路径 no-op。
     *
     * @param type 思考步骤类型
     * @param data 该步骤的具体负载（会被 Jackson 序列化为 JSON）
     */
    public void emitAgentThinking(String type, Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("iteration", currentIteration);
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("data", data);
        agentThinkingSteps.add(envelope);
        if (!streaming || sseEmitter == null)
            return;
        emitSseEvent("agent_thinking", JsonUtils.toJson(envelope));
    }

    // ── Nested types ───────────────────────────────────────────────

    public enum PipelineType {
        SIMPLE_CHAT,
        STREAMING_CHAT,
        AGENT_CHAT
    }

    public record ToolCallRecord(String toolName, String arguments, String toolCallId) {
    }

    public record ToolResultRecord(String toolName, String toolCallId, Object result,
            boolean success, String errorMessage, String model) {
    }

    public record PipelineError(String stageName, String message, Throwable cause,
            boolean recoverable) {
    }
}
