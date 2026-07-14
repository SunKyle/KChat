package com.example.app.pipeline.stage.preprocess;

import com.example.app.dto.MemoryDTO;
import com.example.app.config.CogneeProperties;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.CogneeClient;
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
    private final CogneeClient cogneeClient;
    private final CogneeProperties cogneeProperties;

    @Override
    public Phase getPhase() { return Phase.PREPROCESS; }

    public String getName() {
        return "longTermMemoryStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        try {
            List<MemoryDTO> memories = longTermMemoryService.recall(
                    ctx.getUserId(), ctx.getUserMessage(), 5);
            ctx.setLongTermMemory(memories);

            // Also search Cognee for relevant memories and merge the results
            if (cogneeProperties.isEnabled()) {
                List<String> cogneeResults = cogneeClient.search(
                        ctx.getUserId(), ctx.getUserMessage(), 5);

                if (!cogneeResults.isEmpty()) {
                    List<MemoryDTO> combined = new ArrayList<>(memories);
                    for (int i = 0; i < cogneeResults.size(); i++) {
                        combined.add(MemoryDTO.builder()
                                .content(cogneeResults.get(i))
                                .type("KNOWLEDGE")
                                .userId(ctx.getUserId())
                                .importance(5)
                                .build());
                    }
                    ctx.setLongTermMemory(combined);
                    log.info("[LongTermMemoryStage] Merged {} Cognee results with {} local memories",
                            cogneeResults.size(), memories.size());
                }
            }
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
