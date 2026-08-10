package com.example.app.pipeline.stage.agent;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent Skill 解析阶段
 *
 * MVP 阶段为空实现：尚无 Skill 实体与持久化逻辑。
 * 后续可在此处根据用户消息匹配激活的 Skill，写入 ctx.activeSkillId。
 *
 * order=330（PREPROCESS 阶段，在 longTermMemoryStage 之后、userProfileFormatStage 之前）
 */
@Component
@Slf4j
public class SkillResolutionStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() {
        return Phase.PREPROCESS;
    }

    @Override
    public String getName() {
        return "skillResolutionStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        log.debug("[SkillResolution] Agent mode active, skill resolution skipped (MVP no-op)");
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 330;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
