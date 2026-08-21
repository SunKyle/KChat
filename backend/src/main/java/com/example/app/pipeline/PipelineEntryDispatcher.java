package com.example.app.pipeline;

import com.example.app.pipeline.context.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pipeline 入口调度器 — 统一封装 Agent 模式的入口分支逻辑。
 *
 * <p>ChatService（同步）与 StreamingService（流式）共享同一份调度逻辑，
 * 避免两处重复 {@code explicitSingleSkill} 判断 + {@code executeWithAgentLoop}
 * vs {@code executeWithOrchestratorLoop} 分支。
 *
 * <p>调用方负责线程模型（同步直接调用 / 流式在异步线程中调用），
 * 本调度器只关注 Pipeline 的阶段编排。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PipelineEntryDispatcher {

    private final ContextPipelineExecutor pipelineExecutor;

    /**
     * 简单聊天（非 Agent）路径。
     *
     * <p>运行全部阶段：PREPROCESS → ASSEMBLY → EXECUTION → POSTPROCESS → OBSERVABILITY。
     */
    public void executeSimpleChat(ConversationContext ctx) {
        ctx.setPipelineType(ConversationContext.PipelineType.SIMPLE_CHAT);
        pipelineExecutor.execute(ctx);
    }

    /**
     * Agent 聊天路径（同步 + 流式通用）。
     *
     * <p>根据是否显式指定 Skill 选择单帧 ReAct 或双层 Orchestrator Loop，
     * 结束后显式调用后处理（POSTPROCESS + OBSERVABILITY）。
     *
     * <p>线程模型由调用方决定：
     * <ul>
     *   <li>ChatService：同步直接调用</li>
     *   <li>StreamingService：在异步线程中调用</li>
     * </ul>
     */
    public void executeAgentChat(ConversationContext ctx) {
        ctx.setPipelineType(ConversationContext.PipelineType.AGENT_CHAT);
        dispatchAgentLoop(ctx);
        pipelineExecutor.executePostProcessing(ctx);
    }

    /**
     * Agent 循环入口调度。
     *
     * <p>两条链路：
     * <ol>
     *   <li>用户通过 {@code /} 手动选了 Skill（skillId 非空）→ 走
     *       {@code executeWithAgentLoop}（单帧单 Skill 原子工具 ReAct）</li>
     *   <li>未手动选 Skill（skillId 为空）→ 走 {@code executeWithOrchestratorLoop}
     *       （双层 ReAct：顶层 LLM 只看到 Skill 伪函数，通过 SkillExecutor
     *       嵌套进入 Specialist 帧）</li>
     * </ol>
     */
    private void dispatchAgentLoop(ConversationContext ctx) {
        boolean explicitSingleSkill = ctx.getSkillId() != null && !ctx.getSkillId().isBlank();
        if (explicitSingleSkill) {
            log.info("[Dispatcher] Agent mode: explicit single skillId={}, use executeWithAgentLoop",
                    ctx.getSkillId());
            pipelineExecutor.executeWithAgentLoop(ctx);
        } else {
            log.info("[Dispatcher] Agent mode: no explicit skill, use executeWithOrchestratorLoop (double-decker ReAct)");
            pipelineExecutor.executeWithOrchestratorLoop(ctx);
        }
    }
}
