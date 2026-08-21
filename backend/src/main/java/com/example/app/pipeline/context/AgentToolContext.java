package com.example.app.pipeline.context;

import com.example.app.dto.Artifact;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 模式下的工具调用上下文，包括工具调用记录、结果、启用列表和思考过程。
 *
 * <p>并发安全：流式 Agent 路径下，LLM 回调线程与主线程可能交替访问这些集合，
 * 因此默认使用线程安全实现（{@link CopyOnWriteArrayList} / {@link ConcurrentHashMap}）。
 * 写少读多的场景下 COW 性能足够；未来引入并行 Agent 时也无需再改。
 */
@Data
@Builder(toBuilder = true)
public class AgentToolContext {
    private boolean agentMode;
    private String activeSkillId;
    private List<Artifact> artifacts;

    @Builder.Default
    private List<ConversationContext.ToolCallRecord> toolCalls = new CopyOnWriteArrayList<>();
    @Builder.Default
    private List<ConversationContext.ToolResultRecord> toolResults = new CopyOnWriteArrayList<>();
    @Builder.Default
    private List<String> enabledToolNames = new CopyOnWriteArrayList<>();
    @Builder.Default
    private Map<String, Object> agentState = new ConcurrentHashMap<>();
    @Builder.Default
    private List<Map<String, Object>> agentThinkingSteps = new CopyOnWriteArrayList<>();
}