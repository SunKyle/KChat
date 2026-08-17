package com.example.app.pipeline.context;

import com.example.app.dto.Artifact;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 模式下的工具调用上下文，包括工具调用记录、结果、启用列表和思考过程。
 */
@Data
@Builder(toBuilder = true)
public class AgentToolContext {
    private boolean agentMode;
    private String activeSkillId;
    private List<Artifact> artifacts;

    @Builder.Default
    private List<ConversationContext.ToolCallRecord> toolCalls = new ArrayList<>();
    @Builder.Default
    private List<ConversationContext.ToolResultRecord> toolResults = new ArrayList<>();
    @Builder.Default
    private List<String> enabledToolNames = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> agentState = new HashMap<>();
    @Builder.Default
    private List<Map<String, Object>> agentThinkingSteps = new ArrayList<>();
}