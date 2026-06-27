package com.example.app.pipeline.stage.assembly;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class MessageAssemblyStage implements ContextPipelineStage {

    @Override
    public String getName() {
        return "messageAssemblyStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<ChatMessage> messages = new ArrayList<>();

        SystemMessage systemMsg = (SystemMessage) ctx.getAgentState().get("assembledSystemMessage");
        if (systemMsg != null) {
            messages.add(systemMsg);
        }

        if (ctx.getShortTermMemory() != null) {
            messages.addAll(ctx.getShortTermMemory());
        }

        if (ctx.getUserMessage() != null) {
            messages.add(UserMessage.from(ctx.getUserMessage()));
        }

        ctx.setAssembledMessages(messages);
    }

    @Override
    public int getOrder() {
        return 430;
    }
}
