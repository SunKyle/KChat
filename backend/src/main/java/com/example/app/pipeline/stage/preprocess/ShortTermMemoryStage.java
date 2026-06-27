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
    public String getName() {
        return "shortTermMemoryStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<ChatMessage> memory = shortTermMemoryService.getMemoryContext(ctx.getConversationId());
        ctx.setShortTermMemory(memory);
    }

    @Override
    public int getOrder() {
        return 300;
    }
}
