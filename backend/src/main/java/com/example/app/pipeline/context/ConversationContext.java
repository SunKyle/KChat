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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rich context object carrying all state through the pipeline.
 *
 * <p>Aggregate root / facade that delegates to 6 sub-context objects for internal
 * organization, while maintaining full backward compatibility with all existing callers.
 *
 * <p>Design constraints:
 * <ul>
 *   <li>Mutable by design — stages modify it in-place during execution
 *   <li>Stage-specific sections use namespaced sub-contexts to avoid collisions
 *   <li>Thread-safe for the lifetime of a single request (not shared across requests)
 * </ul>
 */
public class ConversationContext {

    // ── Sub-contexts ───────────────────────────────────────────────

    private RequestMetadata requestMetadata;
    private PipelineOrchestration pipelineOrchestration;
    private MemoryContext memoryContext;
    private AgentToolContext agentToolContext;
    private AssemblyState assemblyState;
    private ExecutionState executionState;

    // ── Fields not covered by sub-contexts ─────────────────────────
    private String searchContext;
    private WebSearchResult rawSearchResult;
    private String customRules;

    // ── Factory ────────────────────────────────────────────────────

    public static ConversationContext fromRequest(ChatRequest request) {
        RequestMetadata metadata = RequestMetadata.builder()
                .conversationId(request.getConversationId())
                .userId(request.getUserId() != null ? request.getUserId() : "default")
                .userMessage(request.getMessage())
                .model(request.getModel())
                .imageUrls(request.getImageUrls() != null ? request.getImageUrls() : List.of())
                .webSearchEnabled(request.isWebSearch())
                .knowledgeBaseIds(request.getKnowledgeBaseIds() != null
                        ? request.getKnowledgeBaseIds() : List.of())
                .build();

        PipelineOrchestration orchestration = PipelineOrchestration.builder()
                .pipelineType(PipelineOrchestration.PipelineType.SIMPLE_CHAT)
                .maxAgentIterations(5)
                .currentIteration(0)
                .pipelineStartTime(System.currentTimeMillis())
                .build();
        orchestration.getTrace().setStartTime(orchestration.getPipelineStartTime());

        MemoryContext memory = MemoryContext.builder().build();
        AgentToolContext agentTool = AgentToolContext.builder().build();
        AssemblyState assembly = AssemblyState.builder().build();
        ExecutionState execution = ExecutionState.builder().build();

        ConversationContext ctx = new ConversationContext();
        ctx.requestMetadata = metadata;
        ctx.pipelineOrchestration = orchestration;
        ctx.memoryContext = memory;
        ctx.agentToolContext = agentTool;
        ctx.assemblyState = assembly;
        ctx.executionState = execution;
        return ctx;
    }

    // ── Well-known agentState keys (shared between assembly stages) ─

    /**
     * Key for formatted Cognee knowledge graph context (entities + relations + fragments),
     * written by MemoryFormatStage(400), read by SystemPromptAssemblyStage(410).
     */
    public static final String KEY_FORMATTED_MEMORY_COGNEE = "formattedMemoryCogneeGraph";
    /**
     * Key for formatted user profile text, written by UserProfileFormatStage(398),
     * read by SystemPromptAssemblyStage(410).
     */
    public static final String KEY_FORMATTED_USER_PROFILE = "formattedUserProfile";
    /**
     * Key for formatted search context section, written by SearchContextFormatStage(405),
     * read by SystemPromptAssemblyStage(410).
     */
    public static final String KEY_FORMATTED_SEARCH = "formattedSearchContext";
    /**
     * Key for the assembled SystemMessage, written by SystemPromptAssemblyStage(410),
     * read by MessageAssemblyStage(430).
     */
    public static final String KEY_SYSTEM_MESSAGE = "assembledSystemMessage";
    /**
     * Key for the active system prompt template version, written by
     * SystemPromptAssemblyStage(410), read by ModelRoutingStage(500).
     */
    public static final String KEY_PROMPT_TEMPLATE_VERSION = "promptTemplateVersion";
    /**
     * Key for the last AiMessage from ModelRoutingStage in Agent mode, read by
     * ToolCallDetectionStage(610) and ToolResultAssemblyStage(660).
     */
    public static final String KEY_LAST_AI_MESSAGE = "lastAiMessage";

    // ── PipelineType backward compat ───────────────────────────────

    public enum PipelineType {
        SIMPLE_CHAT,
        STREAMING_CHAT,
        AGENT_CHAT
    }

    private static PipelineOrchestration.PipelineType toOrchestrationType(PipelineType type) {
        return PipelineOrchestration.PipelineType.valueOf(type.name());
    }

    private static PipelineType fromOrchestrationType(PipelineOrchestration.PipelineType type) {
        return PipelineType.valueOf(type.name());
    }

    // ── Nested types ───────────────────────────────────────────────

    public record ToolCallRecord(String toolName, String arguments, String toolCallId) {
    }

    public record ToolResultRecord(String toolName, String toolCallId, Object result,
            boolean success, String errorMessage, String model) {
    }

    public record PipelineError(String stageName, String message, Throwable cause,
            boolean recoverable) {
    }

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

    // ═══════════════════════════════════════════════════════════════
    //  Delegation Methods — RequestMetadata
    // ═══════════════════════════════════════════════════════════════

    public String getConversationId() {
        return requestMetadata.getConversationId();
    }

    public void setConversationId(String conversationId) {
        requestMetadata.setConversationId(conversationId);
    }

    public String getUserId() {
        return requestMetadata.getUserId();
    }

    public void setUserId(String userId) {
        requestMetadata.setUserId(userId);
    }

    public String getUserMessage() {
        return requestMetadata.getUserMessage();
    }

    public void setUserMessage(String userMessage) {
        requestMetadata.setUserMessage(userMessage);
    }

    public String getModel() {
        return requestMetadata.getModel();
    }

    public void setModel(String model) {
        requestMetadata.setModel(model);
    }

    public List<String> getImageUrls() {
        return requestMetadata.getImageUrls();
    }

    public void setImageUrls(List<String> imageUrls) {
        requestMetadata.setImageUrls(imageUrls);
    }

    public List<String> getKnowledgeBaseIds() {
        return requestMetadata.getKnowledgeBaseIds();
    }

    public void setKnowledgeBaseIds(List<String> knowledgeBaseIds) {
        requestMetadata.setKnowledgeBaseIds(knowledgeBaseIds);
    }

    public List<String> getKbReferenceNames() {
        return requestMetadata.getKbReferenceNames();
    }

    public void setKbReferenceNames(List<String> kbReferenceNames) {
        requestMetadata.setKbReferenceNames(kbReferenceNames);
    }

    public String getLanguage() {
        return requestMetadata.getLanguage();
    }

    public void setLanguage(String language) {
        requestMetadata.setLanguage(language);
    }

    public boolean isWebSearchEnabled() {
        return requestMetadata.isWebSearchEnabled();
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        requestMetadata.setWebSearchEnabled(webSearchEnabled);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Delegation Methods — PipelineOrchestration
    // ═══════════════════════════════════════════════════════════════

    public PipelineType getPipelineType() {
        return fromOrchestrationType(pipelineOrchestration.getPipelineType());
    }

    public void setPipelineType(PipelineType pipelineType) {
        pipelineOrchestration.setPipelineType(toOrchestrationType(pipelineType));
    }

    public int getMaxAgentIterations() {
        return pipelineOrchestration.getMaxAgentIterations();
    }

    public void setMaxAgentIterations(int maxAgentIterations) {
        pipelineOrchestration.setMaxAgentIterations(maxAgentIterations);
    }

    public int getCurrentIteration() {
        return pipelineOrchestration.getCurrentIteration();
    }

    public void setCurrentIteration(int currentIteration) {
        pipelineOrchestration.setCurrentIteration(currentIteration);
    }

    public long getPipelineStartTime() {
        return pipelineOrchestration.getPipelineStartTime();
    }

    public void setPipelineStartTime(long pipelineStartTime) {
        pipelineOrchestration.setPipelineStartTime(pipelineStartTime);
    }

    public List<String> getExecutedStageNames() {
        return pipelineOrchestration.getExecutedStageNames();
    }

    public Map<String, Long> getStageTimings() {
        return pipelineOrchestration.getStageTimings();
    }

    public List<PipelineError> getErrors() {
        return pipelineOrchestration.getErrors();
    }

    public PipelineTrace getTrace() {
        return pipelineOrchestration.getTrace();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Delegation Methods — MemoryContext
    // ═══════════════════════════════════════════════════════════════

    public List<ChatMessage> getShortTermMemory() {
        return memoryContext.getShortTermMemory();
    }

    public void setShortTermMemory(List<ChatMessage> shortTermMemory) {
        memoryContext.setShortTermMemory(shortTermMemory);
    }

    public QueryAnalysisResult getQueryAnalysisResult() {
        return memoryContext.getQueryAnalysisResult();
    }

    public void setQueryAnalysisResult(QueryAnalysisResult queryAnalysisResult) {
        memoryContext.setQueryAnalysisResult(queryAnalysisResult);
    }

    public Object getCogneeContext() {
        return memoryContext.getCogneeContext();
    }

    public void setCogneeContext(Object cogneeContext) {
        memoryContext.setCogneeContext(cogneeContext);
    }

    public List<MemoryDTO> getNewlyExtractedMemories() {
        return memoryContext.getNewlyExtractedMemories();
    }

    public void setNewlyExtractedMemories(List<MemoryDTO> newlyExtractedMemories) {
        memoryContext.setNewlyExtractedMemories(newlyExtractedMemories);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Delegation Methods — AgentToolContext
    // ═══════════════════════════════════════════════════════════════

    public boolean isAgentMode() {
        return agentToolContext.isAgentMode();
    }

    public void setAgentMode(boolean agentMode) {
        agentToolContext.setAgentMode(agentMode);
    }

    public String getActiveSkillId() {
        return agentToolContext.getActiveSkillId();
    }

    public void setActiveSkillId(String activeSkillId) {
        agentToolContext.setActiveSkillId(activeSkillId);
    }

    public List<Artifact> getArtifacts() {
        return agentToolContext.getArtifacts();
    }

    public void setArtifacts(List<Artifact> artifacts) {
        agentToolContext.setArtifacts(artifacts);
    }

    public List<ToolCallRecord> getToolCalls() {
        return agentToolContext.getToolCalls();
    }

    public List<ToolResultRecord> getToolResults() {
        return agentToolContext.getToolResults();
    }

    public List<String> getEnabledToolNames() {
        return agentToolContext.getEnabledToolNames();
    }

    public Map<String, Object> getAgentState() {
        return agentToolContext.getAgentState();
    }

    public List<Map<String, Object>> getAgentThinkingSteps() {
        return agentToolContext.getAgentThinkingSteps();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Delegation Methods — AssemblyState
    // ═══════════════════════════════════════════════════════════════

    public List<ChatMessage> getAssembledMessages() {
        return assemblyState.getAssembledMessages();
    }

    public void setAssembledMessages(List<ChatMessage> assembledMessages) {
        assemblyState.setAssembledMessages(assembledMessages);
    }

    public int getTokenCount() {
        return assemblyState.getTokenCount();
    }

    public void setTokenCount(int tokenCount) {
        assemblyState.setTokenCount(tokenCount);
    }

    public boolean isTruncated() {
        return assemblyState.isTruncated();
    }

    public void setTruncated(boolean truncated) {
        assemblyState.setTruncated(truncated);
    }

    public String getAiMessageId() {
        return assemblyState.getAiMessageId();
    }

    public void setAiMessageId(String aiMessageId) {
        assemblyState.setAiMessageId(aiMessageId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Delegation Methods — ExecutionState
    // ═══════════════════════════════════════════════════════════════

    public ModelConfig getCustomModelConfig() {
        return executionState.getCustomModelConfig();
    }

    public void setCustomModelConfig(ModelConfig customModelConfig) {
        executionState.setCustomModelConfig(customModelConfig);
    }

    public String getLlmResponse() {
        return executionState.getLlmResponse();
    }

    public void setLlmResponse(String llmResponse) {
        executionState.setLlmResponse(llmResponse);
    }

    public boolean isStreaming() {
        return executionState.isStreaming();
    }

    public void setStreaming(boolean streaming) {
        executionState.setStreaming(streaming);
    }

    public Object getSseEmitter() {
        return executionState.getSseEmitter();
    }

    public void setSseEmitter(Object sseEmitter) {
        executionState.setSseEmitter(sseEmitter);
    }

    public String getGeneratedTitle() {
        return executionState.getGeneratedTitle();
    }

    public void setGeneratedTitle(String generatedTitle) {
        executionState.setGeneratedTitle(generatedTitle);
    }

    public boolean isUserMessagePersisted() {
        return executionState.isUserMessagePersisted();
    }

    public void setUserMessagePersisted(boolean userMessagePersisted) {
        executionState.setUserMessagePersisted(userMessagePersisted);
    }

    public boolean isUserMessageInMemory() {
        return executionState.isUserMessageInMemory();
    }

    public void setUserMessageInMemory(boolean userMessageInMemory) {
        executionState.setUserMessageInMemory(userMessageInMemory);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Direct fields — searchContext / rawSearchResult / customRules
    // ═══════════════════════════════════════════════════════════════

    public String getSearchContext() {
        return searchContext;
    }

    public void setSearchContext(String searchContext) {
        this.searchContext = searchContext;
    }

    public WebSearchResult getRawSearchResult() {
        return rawSearchResult;
    }

    public void setRawSearchResult(WebSearchResult rawSearchResult) {
        this.rawSearchResult = rawSearchResult;
    }

    public String getCustomRules() {
        return customRules;
    }

    public void setCustomRules(String customRules) {
        this.customRules = customRules;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Convenience methods
    // ═══════════════════════════════════════════════════════════════

    /** Cognee 上下文快捷获取（需要转型为 CogneeContext） */
    public CogneeContext getCogneeContextTyped() {
        Object raw = memoryContext.getCogneeContext();
        if (raw instanceof CogneeContext ctx)
            return ctx;
        return null;
    }

    public void setCogneeContext(CogneeContext ctx) {
        memoryContext.setCogneeContext(ctx);
    }

    public void recordStage(String name, long durationMs) {
        pipelineOrchestration.recordStage(name, durationMs);
    }

    public void addError(String stageName, String message, Throwable cause, boolean recoverable) {
        pipelineOrchestration.addError(stageName, message, cause, recoverable);
    }

    public boolean hasErrors() {
        return pipelineOrchestration.hasCriticalErrors();
    }

    /**
     * Emit an SSE event if this is a streaming context and an emitter is present.
     * Silently no-ops for non-streaming contexts.
     */
    public void emitSseEvent(String eventName, String jsonData) {
        if (!executionState.isStreaming() || executionState.getSseEmitter() == null)
            return;
        try {
            SseEmitter emitter = (SseEmitter) executionState.getSseEmitter();
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
     *   "type": "tool_definition" | "llm_call" | "tool_detection" | "tool_execution"
     *         | "tool_assembly" | "final_response",
     *   "iteration": <当前 Agent 循环轮次>,
     *   "timestamp": <毫秒时间戳>,
     *   "data": <各类型自定义负载>
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
        envelope.put("iteration", pipelineOrchestration.getCurrentIteration());
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("data", data);
        agentToolContext.getAgentThinkingSteps().add(envelope);
        if (!executionState.isStreaming() || executionState.getSseEmitter() == null)
            return;
        emitSseEvent("agent_thinking", JsonUtils.toJson(envelope));
    }
}