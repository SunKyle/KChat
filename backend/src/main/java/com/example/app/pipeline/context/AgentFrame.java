package com.example.app.pipeline.context;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Agent 栈帧 —— Skill 调用链中的单层状态容器。
 *
 * <p>每进入一个 Skill（或初始化 Orchestrator）就 push 一帧，执行完 pop。
 * 帧内存放这一层独有的状态，绝不包含共享数据。
 *
 * <p>三种角色：
 * <ul>
 *   <li>ORCHESTRATOR —— 顶层路由帧（bottom，始终存在）
 *   <li>SPECIALIST   —— Skill 专家帧（被 Orchestrator 分派时 push）
 * </ul>
 *
 * <p>帧间通信规则（避免 Skill 间直接依赖）：
 * <ul>
 *   <li>Skill 不能直接读写其他 Skill 的 Frame
 *   <li>Skill 执行完后，outputPayload 通过 pop 回传给父帧作为 tool_result
 *   <li>父帧（Orchestrator 或上层 Skill）通过 toolResults 看到子 Skill 的产出
 * </ul>
 */
@Data
@Builder(toBuilder = true)
public class AgentFrame {

    // ── 身份（是谁）──────────────────────────────────────────

    /**
     * 层级角色。
     * ORCHESTRATOR —— 顶层路由，决定调哪个 Skill；
     * SPECIALIST   —— Skill 专家，内部复用 executeWithAgentLoop 调 Tool。
     */
    public enum Role {
        ORCHESTRATOR,
        SPECIALIST
    }

    private Role role;

    /** 全局唯一的帧 ID（由 AgentStack 的递增计数器分配），用于 trace 和前端分组 */
    private int frameId;

    /** 仅 role=SPECIALIST 时有值：当前 Skill 的 id；Orchestrator 为 null */
    private String skillId;

    /** 仅 role=SPECIALIST：Orchestrator 传给这个 Skill 的参数 */
    private Map<String, Object> inputArgs;

    /**
     * 如果非 null：本帧执行完后，结果要回传给这个 skillId（return 栈）。
     * 为 null 表示完成后直接回 Orchestrator。
     * 用于 Skill→Skill 转派场景（A delegate 给 B，B 完成后回 A 而非 Orchestrator）。
     */
    private String returnToSkillId;

    // ── 状态快照（每层独立一份）───────────────────────────────

    /** 这一层自己的组装状态（assembledMessages / tokenCount / truncated / aiMessageId） */
    private AssemblyState assemblyState;

    /**
     * 这一层自己的工具/Agent 状态（toolCalls / toolResults / enabledToolNames / agentState / thinkingSteps）。
     * 类型用 AgentToolContext，保证 Orchestrator 帧能直接复用 fromRequest 创建的对象（同一引用）。
     * Skill 帧 push 时创建新的 AgentToolContext（artifacts 字段不用，由顶层 ConversationContext 持有）。
     */
    private AgentToolContext agentTool;

    /** 这一层自己的迭代计数 */
    private int iteration;
    private int maxIterations;

    /** 帧创建时间，用于 trace */
    private long frameStartTime;

    // ── 产出（执行完回传给上一层）──────────────────────────────

    /** 这一帧执行完毕后的最终结果，pop 时回传给父帧作为 tool_result */
    private Object outputPayload;

    /**
     * 帧状态。
     * RUNNING    —— 执行中
     * FINISHED   —— 正常完成
     * FAILED     —— 执行失败
     * DELEGATED  —— 转派给其他 Skill，自己等结果回来（return 栈场景）
     */
    public enum FrameStatus {
        RUNNING,
        FINISHED,
        FAILED,
        DELEGATED
    }

    private FrameStatus status;
    private String errorMessage;
}
