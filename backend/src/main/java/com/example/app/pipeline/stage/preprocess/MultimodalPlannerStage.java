package com.example.app.pipeline.stage.preprocess;

import com.example.app.dto.MultimodalPlan;
import com.example.app.dto.MultimodalConfigDTO;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.MultimodalConfigService;
import com.example.app.service.MultimodalPlannerService;
import com.example.app.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MultimodalPlannerStage implements ContextPipelineStage {

    private static final org.slf4j.Logger promptLog =
            org.slf4j.LoggerFactory.getLogger("PROMPT_LOG");

    private final MultimodalPlannerService plannerService;
    private final MultimodalConfigService multimodalConfigService;

    @Override
    public Phase getPhase() {
        return Phase.PREPROCESS;
    }

    public String getName() {
        return "multimodalPlannerStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        MultimodalConfigDTO config = multimodalConfigService.getByUserId(ctx.getUserId());
        MultimodalPlan plan = plannerService.plan(
                ctx.getUserMessage(), ctx.getImageUrls(), config.getPlannerModel());
        ctx.setMultimodalPlan(plan.steps());
        promptLog.info("║  [MultimodalPlanner] Plan: {}", JsonUtils.toJson(plan));
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isMultimodal();
    }

    @Override
    public int getOrder() {
        return 320;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
