package com.example.app.pipeline.stage.preprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.security.InputValidator;
import com.example.app.security.SensitiveFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InputSanitizationStage implements ContextPipelineStage {

    private final InputValidator inputValidator;
    private final SensitiveFilter sensitiveFilter;

    @Override
    public Phase getPhase() { return Phase.PREPROCESS; }

    public String getName() {
        return "inputSanitizationStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String input = ctx.getUserMessage();
        if (input == null) return;

        try {
            String sanitized = inputValidator.validateAndSanitize(input);
            sanitized = sensitiveFilter.sanitize(sanitized);
            ctx.setUserMessage(sanitized);
        } catch (IllegalArgumentException e) {
            log.warn("Input validation failed: {}", e.getMessage());
            ctx.addError(getName(), e.getMessage(), e, false);
        }
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
