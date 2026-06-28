package com.example.app.pipeline.stage.postprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.ConversationService;
import com.example.app.service.TitleGenerationService;
import com.example.app.service.UserSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TitleGenerationStage implements ContextPipelineStage {

    private final UserSettingService userSettingService;
    private final ConversationService conversationService;
    private final TitleGenerationService titleGenerationService;

    @Override
    public Phase getPhase() { return Phase.POSTPROCESS; }

    public String getName() {
        return "titleGenerationStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        try {
            var setting = userSettingService.getOrCreate(ctx.getUserId());
            if (!setting.getAutoTitle()) return;

            var conv = conversationService.getConversation(ctx.getConversationId());
            if (conv == null || !"新对话".equals(conv.getTitle())) return;

            String title = titleGenerationService.generateTitle(
                    ctx.getUserMessage(), ctx.getLlmResponse(), ctx.getModel());
            if (title == null || title.isBlank()) return;

            conversationService.updateConversation(ctx.getConversationId(), title, null);
            ctx.setGeneratedTitle(title);
        } catch (Exception e) {
            log.warn("Title generation failed: {}", e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return 800;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
