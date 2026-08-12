package com.example.app.pipeline.stage.preprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 加载会话级自定义规则（customRules）。
 *
 * <p>从 Conversation 实体读取 customRules 并写入 ConversationContext，
 * 供后续 SystemPromptAssemblyStage(410) 注入到系统提示词模板中。
 *
 * <p>放在 PREPROCESS 阶段末尾（order=395），在所有 ASSEMBLY 阶段之前执行。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationRulesLoadStage implements ContextPipelineStage {

    private final ConversationRepository conversationRepository;

    @Override
    public String getName() {
        return "conversationRulesLoadStage";
    }

    @Override
    public Phase getPhase() {
        return Phase.PREPROCESS;
    }

    @Override
    public int getOrder() {
        return 395;
    }

    @Override
    public void execute(ConversationContext ctx) {
        String conversationId = ctx.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        conversationRepository.findById(conversationId).ifPresentOrElse(
                conversation -> {
                    String rules = conversation.getCustomRules();
                    if (rules != null && !rules.isBlank()) {
                        ctx.setCustomRules(rules);
                        log.debug("[ConversationRules] Loaded custom rules for conversation {} ({} chars)",
                                conversationId, rules.length());
                    }
                },
                () -> log.debug("[ConversationRules] Conversation {} not found, skipping", conversationId)
        );
    }
}
