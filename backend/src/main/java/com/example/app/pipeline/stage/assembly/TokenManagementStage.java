package com.example.app.pipeline.stage.assembly;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.util.TokenEstimator;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenManagementStage implements ContextPipelineStage {

    private final TokenEstimator tokenEstimator;

    @Value("${prompt.token.max-tokens:8192}")
    private int maxTokens;

    @Override
    public Phase getPhase() { return Phase.ASSEMBLY; }

    public String getName() {
        return "tokenManagementStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<ChatMessage> messages = ctx.getAssembledMessages();
        if (messages == null || messages.isEmpty()) return;

        int tokenCount = tokenEstimator.estimate(messages);
        ctx.setTokenCount(tokenCount);

        if (tokenCount > maxTokens) {
            List<ChatMessage> truncated = truncateToTokenLimit(messages, maxTokens);
            ctx.setAssembledMessages(truncated);
            ctx.setTruncated(true);
            ctx.setTokenCount(tokenEstimator.estimate(truncated));
        }
    }

    private List<ChatMessage> truncateToTokenLimit(List<ChatMessage> messages, int maxTokens) {
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> historyMessages = new ArrayList<>();
        ChatMessage lastUserMessage = null;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof SystemMessage) {
                systemMessages.add(msg);
            } else if (msg instanceof UserMessage && i == messages.size() - 1) {
                lastUserMessage = msg;
            } else {
                historyMessages.add(msg);
            }
        }

        int mandatoryTokens = tokenEstimator.estimate(systemMessages)
                + (lastUserMessage != null ? tokenEstimator.estimate(lastUserMessage) : 0);

        if (mandatoryTokens > maxTokens) {
            List<ChatMessage> minimal = new ArrayList<>();
            if (!systemMessages.isEmpty()) {
                minimal.add(systemMessages.get(0));
            }
            if (lastUserMessage != null
                    && tokenEstimator.estimate(minimal) + tokenEstimator.estimate(lastUserMessage) <= maxTokens) {
                minimal.add(lastUserMessage);
            }
            return minimal;
        }

        int availableTokens = maxTokens - mandatoryTokens;
        List<ChatMessage> selectedHistory = new ArrayList<>();
        int currentTokens = 0;

        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = historyMessages.get(i);
            int msgTokens = tokenEstimator.estimate(msg);
            if (currentTokens + msgTokens <= availableTokens) {
                selectedHistory.add(0, msg);
                currentTokens += msgTokens;
            }
        }

        List<ChatMessage> result = new ArrayList<>();
        result.addAll(systemMessages);
        result.addAll(selectedHistory);
        if (lastUserMessage != null) {
            result.add(lastUserMessage);
        }
        return result;
    }

    @Override
    public int getOrder() {
        return 440;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
