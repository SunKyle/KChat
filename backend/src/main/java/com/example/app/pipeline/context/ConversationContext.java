package com.example.app.pipeline.context;

import com.example.app.dto.Artifact;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.KbReference;
import com.example.app.dto.MemoryDTO;
import com.example.app.dto.MessageReference;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.dto.WebSearchResult;
import com.example.app.entity.ModelConfig;
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
    private ExecutionState executionState;

    /**
     * Agent 栈 —— 管理嵌套调用的栈帧结构。
     *
     * <p>Orchestrator 帧承载 AgentToolContext + AssemblyState；Skill 帧 push 时
     * 创建独立的 AgentToolContext + AssemblyState；Facade 方法（getToolCalls /
     * getAssembledMessages 等）统一代理到 stack.peek()。
     *
     * <p>设计保障：Stage 代码通过 Facade 方法访问，零感知栈帧切换。
     */
    private AgentStack agentStack;

    // ── Fields not covered by sub-contexts ─────────────────────────
    private String searchContext;
    private WebSearchResult rawSearchResult;
    private String customRules;

    /**
     * Artifacts 列表 —— 从 AgentToolContext 提升到顶层共享。
     *
     * <p>跨帧可见：无论在 Orchestrator 层还是 Skill 层产生的图片/文件，
     * 都写入这个全局列表，保证前端能渲染所有产物。
     *
     * <p>并发安全：流式 Agent 路径下 LLM 回调线程与主线程都可能写入，
     * 使用 {@link java.util.concurrent.CopyOnWriteArrayList} 保证线程安全。
     */
    private java.util.List<Artifact> artifacts = new java.util.concurrent.CopyOnWriteArrayList<>();

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
                .skillId(request.getSkillId())
                .references(request.getReferences())
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
        ctx.executionState = execution;
        // 构造 AgentStack，把 fromRequest 创建的 agentTool/assembly 迁入 Orchestrator 帧
        // Facade 方法统一代理到 stack.peek()，原有行为完全不变
        ctx.agentStack = new AgentStack(agentTool, assembly, orchestration.getMaxAgentIterations());
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

    /**
     * Key for the active Skill entity, written by SkillResolutionStage(330),
     * read by SystemPromptAssemblyStage(410), ToolDefinitionStage(480),
     * and SkillCompletionHookStage(810).
     */
    public static final String KEY_ACTIVE_SKILL = "activeSkill";

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

    public List<KbReference> getKbReferences() {
        return requestMetadata.getKbReferences();
    }

    public void setKbReferences(List<KbReference> kbReferences) {
        requestMetadata.setKbReferences(kbReferences);
    }

    /**
     * 手动激活的 Skill ID（来自请求参数）。
     * SkillResolutionStage 读取此字段决定是否激活 Skill。
     */
    public String getSkillId() {
        return requestMetadata.getSkillId();
    }

    public void setSkillId(String skillId) {
        requestMetadata.setSkillId(skillId);
    }

    /**
     * 用户消息引用的资源列表（知识库 / 技能），由 ChatRequest 透传，
     * 持久化到 Message.references 列，供历史会话展示"当时引用了什么"。
     */
    public List<MessageReference> getReferences() {
        return requestMetadata.getReferences();
    }

    public void setReferences(List<MessageReference> references) {
        requestMetadata.setReferences(references);
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
    //  Delegation Methods — PipelineOrchestration (部分字段代理到栈帧)
    // ═══════════════════════════════════════════════════════════════

    public PipelineType getPipelineType() {
        return fromOrchestrationType(pipelineOrchestration.getPipelineType());
    }

    public void setPipelineType(PipelineType pipelineType) {
        pipelineOrchestration.setPipelineType(toOrchestrationType(pipelineType));
    }

    /**
     * Orchestrator 帧的最大迭代次数。
     * 注意：Skill 帧有自己的 maxIterations（见 AgentFrame.maxIterations）。
     */
    public int getMaxAgentIterations() {
        return agentStack.peek().getMaxIterations();
    }

    public void setMaxAgentIterations(int maxAgentIterations) {
        agentStack.peek().setMaxIterations(maxAgentIterations);
        // 同步到 PipelineOrchestration 保持向后兼容
        pipelineOrchestration.setMaxAgentIterations(maxAgentIterations);
    }

    /**
     * 当前活跃帧的迭代计数。
     * Skill 帧的 iteration 独立于 Orchestrator 帧。
     */
    public int getCurrentIteration() {
        return agentStack.peek().getIteration();
    }

    public void setCurrentIteration(int currentIteration) {
        agentStack.peek().setIteration(currentIteration);
        // 同步到 PipelineOrchestration 保持向后兼容
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
    //  Delegation Methods — AgentToolContext (代理到 agentStack.peek())
    // ═══════════════════════════════════════════════════════════════

    public boolean isAgentMode() {
        return agentStack.peek().getAgentTool().isAgentMode();
    }

    public void setAgentMode(boolean agentMode) {
        agentStack.peek().getAgentTool().setAgentMode(agentMode);
    }

    public String getActiveSkillId() {
        return agentStack.peek().getSkillId();
    }

    public void setActiveSkillId(String activeSkillId) {
        // Skill 帧的 skillId 在 push 时设置，这里兼容旧代码：设置到当前帧
        agentStack.peek().setSkillId(activeSkillId);
        // 同步到 AgentToolContext 保持向后兼容
        agentStack.peek().getAgentTool().setActiveSkillId(activeSkillId);
    }

    /**
     * Artifacts 列表（提升到顶层共享，跨帧可见）。
     * 任何帧产生的图片/文件都写入这个全局列表。
     */
    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts;
    }

    public List<ToolCallRecord> getToolCalls() {
        return agentStack.peek().getAgentTool().getToolCalls();
    }

    public List<ToolResultRecord> getToolResults() {
        return agentStack.peek().getAgentTool().getToolResults();
    }

    public List<String> getEnabledToolNames() {
        return agentStack.peek().getAgentTool().getEnabledToolNames();
    }

    public Map<String, Object> getAgentState() {
        return agentStack.peek().getAgentTool().getAgentState();
    }

    public List<Map<String, Object>> getAgentThinkingSteps() {
        return agentStack.peek().getAgentTool().getAgentThinkingSteps();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Delegation Methods — AssemblyState (代理到 agentStack.peek())
    // ═══════════════════════════════════════════════════════════════

    public List<ChatMessage> getAssembledMessages() {
        return agentStack.peek().getAssemblyState().getAssembledMessages();
    }

    public void setAssembledMessages(List<ChatMessage> assembledMessages) {
        agentStack.peek().getAssemblyState().setAssembledMessages(assembledMessages);
    }

    public int getTokenCount() {
        return agentStack.peek().getAssemblyState().getTokenCount();
    }

    public void setTokenCount(int tokenCount) {
        agentStack.peek().getAssemblyState().setTokenCount(tokenCount);
    }

    public boolean isTruncated() {
        return agentStack.peek().getAssemblyState().isTruncated();
    }

    public void setTruncated(boolean truncated) {
        agentStack.peek().getAssemblyState().setTruncated(truncated);
    }

    public String getAiMessageId() {
        return agentStack.peek().getAssemblyState().getAiMessageId();
    }

    public void setAiMessageId(String aiMessageId) {
        agentStack.peek().getAssemblyState().setAiMessageId(aiMessageId);
    }

    /**
     * 获取 AgentStack（供 OrchestratorLoop 驱动 push/pop）。
     */
    public AgentStack getAgentStack() {
        return agentStack;
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

    /**
     * 设置流式完成后的后处理回调钩子。
     *
     * <p>由 {@code StreamingService} 在执行 pipeline 前注入，
     * 指向 {@code pipelineExecutor.executePostProcessing(ctx)}。
     * Stage（如 ModelRoutingStage）在非 Agent 流式回调中调用
     * {@link #runPostStreamingHook()} 触发后处理，
     * 避免 Stage 反向依赖 {@link ContextPipelineExecutor} 造成循环依赖。
     */
    public void setPostStreamingHook(Runnable hook) {
        executionState.setPostStreamingHook(hook);
    }

    /**
     * 触发流式完成后的后处理回调。若无钩子则 no-op。
     */
    public void runPostStreamingHook() {
        Runnable hook = executionState.getPostStreamingHook();
        if (hook != null) {
            hook.run();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Client disconnect — SSE 断连后的取消信号
    // ═══════════════════════════════════════════════════════════════

    /**
     * 标记客户端已断连。
     *
     * <p>由 SSE 容器的 onTimeout / onError 回调调用（SSE 容器线程）。
     * 设置后，LLM 回调线程、Pipeline 循环、StreamingService 异步 catch
     * 检测到标志后会提前短路，避免继续生成无用 token。
     *
     * <p>幂等：可被多次调用，第二次及之后为 no-op。
     */
    public void markClientCancelled() {
        if (!executionState.isClientCancelled()) {
            executionState.setClientCancelled(true);
        }
    }

    /**
     * 客户端是否已断连。
     *
     * <p>volatile 读，保证 SSE 容器线程的写入对其他线程立即可见。
     */
    public boolean isClientCancelled() {
        return executionState.isClientCancelled();
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

    /**
     * Cognee 长期记忆上下文（中性载体，不依赖 CogneeClient 实现类型）。
     * 由 LongTermMemoryStage 写入，MemoryFormatStage 读取。
     */
    public CogneeMemoryContext getCogneeContextTyped() {
        Object raw = memoryContext.getCogneeContext();
        if (raw instanceof CogneeMemoryContext ctx)
            return ctx;
        return null;
    }

    public void setCogneeContext(CogneeMemoryContext ctx) {
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
        AgentFrame currentFrame = agentStack.peek();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("frameId", agentStack.currentFrameId());
        envelope.put("role", currentFrame.getRole().name());
        envelope.put("skillId", currentFrame.getSkillId());
        envelope.put("iteration", currentFrame.getIteration());
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("data", data);
        currentFrame.getAgentTool().getAgentThinkingSteps().add(envelope);
        if (!executionState.isStreaming() || executionState.getSseEmitter() == null)
            return;
        emitSseEvent("agent_thinking", JsonUtils.toJson(envelope));
    }
}