package com.example.app.pipeline.stage.execution;

import com.example.app.dto.MultimodalArtifact;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MultimodalCompositorStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() {
        return Phase.EXECUTION;
    }

    public String getName() {
        return "multimodalCompositorStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        if (ctx.getArtifacts() == null) {
            ctx.setArtifacts(new ArrayList<>());
        }
        String response = ctx.getLlmResponse();
        if ((response == null || response.isBlank()) && ctx.getArtifacts().isEmpty()) {
            ctx.setLlmResponse("未能生成多模态结果，请检查模型配置或稍后重试。");
        }
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isMultimodal();
    }

    @Override
    public int getOrder() {
        return 515;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
