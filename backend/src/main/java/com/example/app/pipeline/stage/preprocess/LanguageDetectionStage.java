package com.example.app.pipeline.stage.preprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LanguageDetectionStage implements ContextPipelineStage {

    private final UserProfileService userProfileService;

    @Override
    public String getName() {
        return "languageDetectionStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String language = userProfileService.getLanguage(ctx.getUserId());
        ctx.setLanguage(language);
    }

    @Override
    public int getOrder() {
        return 110;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
