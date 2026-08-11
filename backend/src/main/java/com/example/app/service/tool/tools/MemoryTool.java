package com.example.app.service.tool.tools;

import com.example.app.dto.MemoryDTO;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.service.LongTermMemoryService;
import com.example.app.service.tool.ToolComponent;
import com.example.app.service.tool.UserContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 长期记忆工具
 *
 * 暴露长期记忆相关工具方法，供 LLM 在 Agent 模式下主动召回、查询、保存记忆。
 * 复用 {@link LongTermMemoryService} 的能力，userId 通过
 * {@link UserContextHolder} 获取。
 *
 * <p>注意：pipeline 的 LongTermMemoryStage 会在每轮对话开始时被动注入相关记忆，
 * 本工具用于 LLM 在推理过程中需要主动检索更多记忆或保存新记忆的场景。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryTool implements ToolComponent {

    private final LongTermMemoryService longTermMemoryService;

    @Tool("从用户长期记忆中按语义召回相关内容。当需要回忆用户偏好、历史事件、事实等记忆时调用。")
    String recallMemory(
            @P("查询文本，如'用户喜欢的食物'、'上周讨论的项目'") String query,
            @P("返回数量上限，可选，默认 5") Integer topK) {
        String userId = UserContextHolder.get();
        int limit = (topK != null && topK > 0) ? topK : 5;
        log.info("[MemoryTool] recallMemory: userId={}, query='{}', topK={}", userId, query, limit);

        try {
            List<MemoryDTO> memories = longTermMemoryService.recall(userId, query, limit);
            return formatMemoryList("语义召回「" + query + "」", memories);
        } catch (Exception e) {
            log.error("[MemoryTool] recallMemory failed", e);
            return "记忆召回失败：" + e.getMessage();
        }
    }

    @Tool("列出当前用户的所有长期记忆，可按类型过滤。当用户想查看已记录的全部记忆时调用。")
    String listMemories(
            @P("记忆类型过滤，可选。可选值：KNOWLEDGE/PREFERENCE/EVENT/FACT/RELATIONSHIP，不传则列出全部") String type) {
        String userId = UserContextHolder.get();
        log.info("[MemoryTool] listMemories: userId={}, type={}", userId, type);

        try {
            List<MemoryDTO> memories;
            if (type == null || type.isBlank()) {
                memories = longTermMemoryService.findByUserId(userId);
            } else {
                memories = longTermMemoryService.findByUserIdAndType(userId, type);
            }
            return formatMemoryList(type != null && !type.isBlank()
                    ? "类型「" + type + "」记忆" : "所有记忆", memories);
        } catch (Exception e) {
            log.error("[MemoryTool] listMemories failed", e);
            return "查询记忆失败：" + e.getMessage();
        }
    }

    @Tool("主动保存一条长期记忆。当用户明确要求记住某事或对话中出现值得长期保留的信息时调用。")
    String saveMemory(
            @P("记忆内容") String content,
            @P("记忆类型：KNOWLEDGE(知识)/PREFERENCE(偏好)/EVENT(事件)/FACT(事实)/RELATIONSHIP(关系)，可选，默认 KNOWLEDGE") String type,
            @P("重要性 1-10，可选，默认 5") Integer importance) {
        String userId = UserContextHolder.get();
        String memoryType = (type != null && !type.isBlank()) ? type : "KNOWLEDGE";
        int importanceVal = (importance != null) ? importance : 5;
        log.info("[MemoryTool] saveMemory: userId={}, type={}, importance={}", userId, memoryType, importanceVal);

        try {
            // 校验类型合法性
            try {
                MemoryType.valueOf(memoryType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return "保存失败：无效的记忆类型 '" + memoryType + "'。可选值：KNOWLEDGE/PREFERENCE/EVENT/FACT/RELATIONSHIP";
            }

            longTermMemoryService.store(userId, content, memoryType);
            return "记忆已保存。类型: " + memoryType + "，重要性: " + importanceVal;
        } catch (Exception e) {
            log.error("[MemoryTool] saveMemory failed", e);
            return "保存记忆失败：" + e.getMessage();
        }
    }

    /** 把记忆列表格式化为 LLM 易读的文本。 */
    private String formatMemoryList(String title, List<MemoryDTO> memories) {
        if (memories == null || memories.isEmpty()) {
            return title + "：没有找到任何记忆。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("，共 ").append(memories.size()).append(" 条：\n\n");
        for (int i = 0; i < memories.size(); i++) {
            MemoryDTO m = memories.get(i);
            sb.append(i + 1).append(". ");
            sb.append("[").append(m.getMemoryType()).append("] ");
            sb.append("重要性: ").append(m.getImportance());
            sb.append("\n   内容: ").append(m.getContent());
            sb.append("\n");
        }
        return sb.toString();
    }
}
