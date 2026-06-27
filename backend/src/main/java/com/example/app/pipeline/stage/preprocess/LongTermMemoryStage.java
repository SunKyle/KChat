package com.example.app.pipeline.stage.preprocess;

import com.example.app.dto.MemoryDTO;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.LongTermMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryStage implements ContextPipelineStage {

    private final LongTermMemoryService longTermMemoryService;

    @Override
    public String getName() {
        return "longTermMemoryStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        try {
            List<MemoryDTO> memories = longTermMemoryService.recall(
                    ctx.getUserId(), ctx.getUserMessage(), 5);
            ctx.setLongTermMemory(memories);
        } catch (Exception e) {
            log.warn("Long-term memory recall failed: {}", e.getMessage());
            ctx.setLongTermMemory(new ArrayList<>());
        }
    }

    @Override
    public int getOrder() {
        return 310;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
