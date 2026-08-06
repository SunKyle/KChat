package com.example.app.pipeline.stage.assembly;

import com.example.app.dto.MemoryDTO;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class MemoryFormatStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() { return Phase.ASSEMBLY; }

    public String getName() {
        return "memoryFormatStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<MemoryDTO> memories = ctx.getLongTermMemory();
        String formatted = formatMemories(memories);
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY, formatted);
    }

    private String formatMemories(List<MemoryDTO> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        List<MemoryDTO> sorted = new ArrayList<>(memories);
        sorted.sort((a, b) -> Integer.compare(b.getImportance(), a.getImportance()));

        if (sorted.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("长期记忆（可能过时，仅作参考）：\n");
        for (MemoryDTO memory : sorted) {
            sb.append("- ");
            LocalDateTime time = memory.getUpdatedAt() != null ? memory.getUpdatedAt() : memory.getCreatedAt();
            if (time != null) {
                sb.append("[").append(time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).append("] ");
            }
            sb.append(memory.getContent());
            List<String> tags = new ArrayList<>();
            if (memory.getConfidence() != null) {
                tags.add("置信度 " + Math.round(memory.getConfidence() * 100) + "%");
            }
            if (memory.getSource() != null && !memory.getSource().isBlank()) {
                tags.add("来源 " + memory.getSource());
            }
            if (!tags.isEmpty()) {
                sb.append("（").append(String.join("，", tags)).append("）");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    public int getOrder() {
        return 400;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
