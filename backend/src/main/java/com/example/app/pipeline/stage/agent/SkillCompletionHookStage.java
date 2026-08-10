package com.example.app.pipeline.stage.agent;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent Skill 完成钩子阶段
 *
 * MVP 阶段为空实现：尚无 Skill 实体与持久化逻辑。
 * 后续可在此处执行 Skill 完成后的回调（如：更新 Skill 状态、触发通知、记录产物等）。
 *
 * order=810（POSTPROCESS 阶段，在 titleGenerationStage(800) 之后、streamingDoneStage(850) 之前）
 */
@Component
@Slf4j
public class SkillCompletionHookStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() {
        return Phase.POSTPROCESS;
    }

    @Override
    public String getName() {
        return "skillCompletionHookStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        log.debug("[SkillCompletionHook] Agent mode completion hook skipped (MVP no-op)");
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 810;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
