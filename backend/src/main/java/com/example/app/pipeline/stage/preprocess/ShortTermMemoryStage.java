package com.example.app.pipeline.stage.preprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.ShortTermMemoryService;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShortTermMemoryStage implements ContextPipelineStage {

    private final ShortTermMemoryService shortTermMemoryService;

    @Override
    public Phase getPhase() { return Phase.PREPROCESS; }

    public String getName() {
        return "shortTermMemoryStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<ChatMessage> memory = shortTermMemoryService.getMemoryContext(ctx.getConversationId());

        // Strip the trailing user message if it matches the current input.
        // This happens when a previous request's ShortTermMemoryUpdateStage stored
        // the user message into memory — without this, MessageAssemblyStage would
        // add it again, causing a duplicate in the prompt.
        if (!memory.isEmpty() && ctx.getUserMessage() != null) {
            ChatMessage last = memory.get(memory.size() - 1);
            if (last instanceof dev.langchain4j.data.message.UserMessage
                    && ctx.getUserMessage().equals(last.text())) {
                memory = memory.subList(0, memory.size() - 1);
            }
        }

        ctx.setShortTermMemory(memory);
    }

    @Override
    public int getOrder() {
        return 300;
    }
}
