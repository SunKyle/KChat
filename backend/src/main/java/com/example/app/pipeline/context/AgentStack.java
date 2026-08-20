package com.example.app.pipeline.context;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 栈 —— 管理嵌套调用的栈帧结构。
 *
 * <p>核心设计原则：
 * <ul>
 *   <li>栈底始终是 Orchestrator 帧（任何请求都有，即使没启用 Skill）
 *   <li>每进入一个 Skill push 一帧，执行完 pop，结果回传给父帧
 *   <li>帧间数据严格隔离，只通过 pop 时的 outputPayload 通信
 *   <li>提供受控的 push/pop/peek API，不允许外部直接操作 frames list
 * </ul>
 *
 * <p>MVP 阶段（ReAct 串行模式）：
 * <ul>
 *   <li>Orchestrator 帧始终在 bottom
 *   <li>Skill 帧按 Orchestrator 决策 push/pop，同时只有一帧活跃
 *   <li>peek() 返回当前活跃帧，Stage 通过 ctx 代理读写它
 * </ul>
 *
 * <p>未来扩展（并行模式）：
 * <ul>
 *   <li>多个无依赖 Skill 可同时 push（但 ReAct 串行模式下不会发生）
 *   <li>并行模式需要配合 ThreadLocal 绑定当前帧，MVP 不实现
 * </ul>
 */
@Getter
public class AgentStack {

    private final List<AgentFrame> frames = new ArrayList<>();

    /** 单调递增的帧 ID 计数器，保证每个 push 的帧都有全局唯一 ID（即使 pop 后再 push 也不复用） */
    private int frameIdCounter = 0;

    /**
     * 初始化：push Orchestrator 帧。
     * 任何请求都从 Orchestrator 开始（即使没启用 Skill，也用 Orchestrator 帧承载默认行为）。
     */
    public AgentStack() {
        AgentFrame orch = AgentFrame.builder()
                .role(AgentFrame.Role.ORCHESTRATOR)
                .frameId(frameIdCounter++)
                .skillId(null)
                .assemblyState(AssemblyState.builder().build())
                .agentTool(AgentToolContext.builder().build())
                .iteration(0)
                .maxIterations(10)  // Orchestrator 分派轮次上限
                .frameStartTime(System.currentTimeMillis())
                .status(AgentFrame.FrameStatus.RUNNING)
                .build();
        frames.add(orch);
    }

    /**
     * 初始化：push Orchestrator 帧，直接复用现有的 AgentToolContext 和 AssemblyState 对象。
     *
     * <p>用于 ConversationContext.fromRequest() —— 让 Orchestrator 帧直接持有 fromRequest
     * 创建的对象（同一引用，非副本），保证现有 Facade 方法（代理到 stack.peek()）行为不变。
     *
     * @param existingAgentTool  fromRequest 创建的 AgentToolContext（Orchestrator 帧直接持有它）
     * @param existingAssembly   fromRequest 创建的 AssemblyState（Orchestrator 帧直接持有它）
     * @param maxAgentIterations Orchestrator 分派轮次上限（沿用请求配置）
     */
    public AgentStack(AgentToolContext existingAgentTool, AssemblyState existingAssembly, int maxAgentIterations) {
        AgentFrame orch = AgentFrame.builder()
                .role(AgentFrame.Role.ORCHESTRATOR)
                .frameId(frameIdCounter++)
                .skillId(null)
                .assemblyState(existingAssembly != null ? existingAssembly : AssemblyState.builder().build())
                .agentTool(existingAgentTool != null ? existingAgentTool : AgentToolContext.builder().build())
                .iteration(0)
                .maxIterations(maxAgentIterations > 0 ? maxAgentIterations : 10)
                .frameStartTime(System.currentTimeMillis())
                .status(AgentFrame.FrameStatus.RUNNING)
                .build();
        frames.add(orch);
    }

    /**
     * 进入 Skill：push 新的 SPECIALIST 帧。
     *
     * @param skillId        Skill ID
     * @param inputArgs      Orchestrator 传入的参数
     * @param returnToSkillId 完成后回传给哪个 Skill（null=回 Orchestrator）
     * @param maxIterations  Skill 内部 Agent 最大迭代次数
     */
    public void pushSkillFrame(
            String skillId,
            Map<String, Object> inputArgs,
            String returnToSkillId,
            int maxIterations) {
        AgentFrame frame = AgentFrame.builder()
                .role(AgentFrame.Role.SPECIALIST)
                .frameId(frameIdCounter++)
                .skillId(skillId)
                .inputArgs(inputArgs)
                .returnToSkillId(returnToSkillId)
                .assemblyState(AssemblyState.builder().build())
                .agentTool(AgentToolContext.builder()
                        .agentMode(true)  // SPECIALIST 帧必须开启 Agent 模式才能调用原子 Tool
                        .build())
                .iteration(0)
                .maxIterations(maxIterations)
                .frameStartTime(System.currentTimeMillis())
                .status(AgentFrame.FrameStatus.RUNNING)
                .build();
        frames.add(frame);
    }

    /**
     * Skill 执行完毕，出栈。
     *
     * <p>pop 前会把当前帧的 thinkingSteps 合并到父帧（带 childFrameId 标记），
     * 保证 trace 完整性。pop 后父帧成为新的活跃帧。
     *
     * @return 被弹出的帧（调用方取 outputPayload 作为 tool_result 回填）
     * @throws IllegalStateException 如果尝试 pop Orchestrator 帧
     */
    public AgentFrame popFrame() {
        if (frames.size() <= 1) {
            throw new IllegalStateException("Cannot pop Orchestrator frame (stack bottom)");
        }
        AgentFrame popped = frames.remove(frames.size() - 1);
        AgentFrame parent = frames.get(frames.size() - 1);

        // 合并子帧 thinkingSteps 到父帧（带 childFrameId 标记）
        for (Map<String, Object> step : popped.getAgentTool().getAgentThinkingSteps()) {
            Map<String, Object> enriched = new LinkedHashMap<>(step);
            enriched.put("childFrameId", popped.getFrameId());
            enriched.put("childSkillId", popped.getSkillId());
            parent.getAgentTool().getAgentThinkingSteps().add(enriched);
        }
        return popped;
    }

    /** 当前活跃帧（栈顶） */
    public AgentFrame peek() {
        return frames.get(frames.size() - 1);
    }

    /** 栈深度（1=只有 Orchestrator，2=Orchestrator+1个Skill，...） */
    public int depth() {
        return frames.size();
    }

    /** 当前活跃帧的全局唯一 ID（单调递增，不复用），用于 trace 标记和前端分组 */
    public int currentFrameId() {
        return peek().getFrameId();
    }

    /** 是否在 Orchestrator 层（栈深度=1） */
    public boolean isAtOrchestrator() {
        return frames.size() == 1;
    }

    /**
     * 按 skillId 查找帧索引（用于 return 栈场景）。
     * 从栈顶往下找，返回第一个匹配的索引；找不到返回 -1。
     */
    public int findFrameIndexBySkillId(String skillId) {
        if (skillId == null) return -1;
        for (int i = frames.size() - 1; i >= 0; i--) {
            AgentFrame f = frames.get(i);
            if (skillId.equals(f.getSkillId())) return i;
        }
        return -1;
    }
}
