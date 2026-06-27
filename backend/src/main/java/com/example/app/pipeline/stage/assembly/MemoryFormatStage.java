package com.example.app.pipeline.stage.assembly;

import com.example.app.dto.MemoryDTO;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class MemoryFormatStage implements ContextPipelineStage {

    @Override
    public String getName() {
        return "memoryFormatStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<MemoryDTO> memories = ctx.getLongTermMemory();
        String formatted = formatMemories(memories);
        ctx.getAgentState().put("formattedLongTermMemory", formatted);
    }

    private String formatMemories(List<MemoryDTO> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        List<MemoryDTO> sorted = new ArrayList<>(memories);
        sorted.sort((a, b) -> Integer.compare(b.getImportance(), a.getImportance()));

        StringBuilder sb = new StringBuilder();
        sb.append("用户背景：\n");
        for (MemoryDTO memory : sorted) {
            sb.append("- ").append(memory.getContent()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public int getOrder() {
        return 410;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
