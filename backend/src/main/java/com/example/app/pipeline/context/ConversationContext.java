package com.example.app.pipeline.context;

import com.example.app.dto.ChatRequest;
import com.example.app.dto.MemoryDTO;
import com.example.app.dto.WebSearchResult;
import com.example.app.entity.ModelConfig;
import com.example.app.util.JsonUtils;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

/**
 * Rich context object carrying all state through the pipeline.
 *
 * Replaces the scattered parameter passing pattern where services passed
 * (shortTermMemory, longTermMemory, userMessage, language, conversationId, searchContext)
 * as individual arguments. All pipeline stages read from and write to this single context.
 *
 * Design constraints:
 * - Mutable by design — stages modify it in-place during execution
 * - Builder pattern for initial construction from ChatRequest
 * - Stage-specific sections use namespaced groups to avoid collisions
 * - Thread-safe for the lifetime of a single request (not shared across requests)
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

    // ── Context enrichment ─────────────────────────────────────────
    private String language;
    private String searchContext;
    private WebSearchResult rawSearchResult;

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

    // ── Agent / tool state (future phases) ────────────────────────
    private final List<ToolCallRecord> toolCalls = new ArrayList<>();
    private final List<ToolResultRecord> toolResults = new ArrayList<>();
    private final List<String> enabledToolNames = new ArrayList<>();
    private final Map<String, Object> agentState = new HashMap<>();

    // ── Post-processing outputs ────────────────────────────────────
    private String generatedTitle;

    // ── Telemetry ──────────────────────────────────────────────────
    private long pipelineStartTime;
    private final Map<String, Long> stageTimings = new LinkedHashMap<>();

    // ── Error handling ─────────────────────────────────────────────
    private final List<PipelineError> errors = new ArrayList<>();

    // ── Factory ────────────────────────────────────────────────────

    public static ConversationContext fromRequest(ChatRequest request) {
        return ConversationContext.builder()
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
    }

    // ── Well-known agentState keys (shared between assembly stages) ─

    /** Key for formatted long-term memory text, written by MemoryFormatStage(400), read by SystemPromptAssemblyStage(410) */
    public static final String KEY_FORMATTED_MEMORY = "formattedLongTermMemory";
    /** Key for formatted user profile text, written by UserProfileFormatStage(398), read by SystemPromptAssemblyStage(410) */
    public static final String KEY_FORMATTED_USER_PROFILE = "formattedUserProfile";
    /** Key for formatted search context section, written by SearchContextFormatStage(405), read by SystemPromptAssemblyStage(410) */
    public static final String KEY_FORMATTED_SEARCH = "formattedSearchContext";
    /** Key for the assembled SystemMessage, written by SystemPromptAssemblyStage(410), read by MessageAssemblyStage(430) */
    public static final String KEY_SYSTEM_MESSAGE = "assembledSystemMessage";
    /** Key for the active system prompt template version, written by SystemPromptAssemblyStage(410), read by ModelRoutingStage(500) */
    public static final String KEY_PROMPT_TEMPLATE_VERSION = "promptTemplateVersion";

    // ── Convenience ────────────────────────────────────────────────

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
        if (!streaming || sseEmitter == null) return;
        try {
            SseEmitter emitter = (SseEmitter) sseEmitter;
            emitter.send(SseEmitter.event().name(eventName).data(jsonData));
        } catch (Exception e) {
            // emitter may already be closed/completed — don't disrupt the pipeline
        }
    }

    // ── Nested types ───────────────────────────────────────────────

    public enum PipelineType {
        SIMPLE_CHAT,
        STREAMING_CHAT,
        AGENT_CHAT
    }

    public record ToolCallRecord(String toolName, String arguments, String toolCallId) {}

    public record ToolResultRecord(String toolName, String toolCallId, Object result,
                                   boolean success, String errorMessage) {}

    public record PipelineError(String stageName, String message, Throwable cause,
                                boolean recoverable) {}
}
