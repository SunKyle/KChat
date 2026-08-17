package com.example.app.service.tool.tools;

import com.example.app.service.CogneeClient;
import com.example.app.service.tool.ToolComponent;
import com.example.app.service.tool.UserContextHolder;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Cognee 记忆工具
 *
 * 提供对 Cognee 知识图谱的读写能力，替代已被移除的 JPA 长期记忆工具。
 * 通过 CogneeClient 操作语义化知识图谱，支持记忆召回、存储和列表查看。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CogneeMemoryTool implements ToolComponent {

    private final CogneeClient cogneeClient;

    @Tool("""
            从长期记忆中搜索与查询相关的内容。
            输入自然语言查询，返回相关的记忆片段（包括实体、关系和上下文）。
            适用于：当需要了解用户的历史信息、偏好、已保存的知识时调用。
            此工具搜索的是已持久化的知识图谱，而非当前会话的短期记忆。
            """)
    public String recallMemory(
            String query,
            Integer topK) {
        if (query == null || query.isBlank()) {
            return "错误：查询内容不能为空";
        }

        String userId = UserContextHolder.get();
        int k = (topK != null && topK > 0) ? Math.min(topK, 50) : 10;

        try {
            CogneeClient.RecallWithContextResult result = cogneeClient.recallWithContext(
                    userId, query, k, null);

            List<CogneeClient.RecallResult> fragments = result.fragments();
            List<String> entities = result.entities();
            List<CogneeClient.CogneeRelationRecord> relations = result.relations();

            if ((fragments == null || fragments.isEmpty())
                    && (entities == null || entities.isEmpty())
                    && (relations == null || relations.isEmpty())) {
                log.info("[CogneeMemoryTool] No memories found for query: '{}'", query);
                return "未找到与「" + query + "」相关的记忆。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到以下相关记忆：\n");

            // 相关实体
            if (entities != null && !entities.isEmpty()) {
                sb.append("\n关联实体：").append(String.join(", ", entities)).append("\n");
            }

            // 语义片段
            if (fragments != null && !fragments.isEmpty()) {
                sb.append("\n语义相关片段：\n");
                for (CogneeClient.RecallResult f : fragments) {
                    sb.append("- [相似度 ").append(String.format("%.2f", f.score())).append("] ")
                            .append(f.text()).append("\n");
                }
            }

            // 关系
            if (relations != null && !relations.isEmpty()) {
                sb.append("\n关联关系：\n");
                for (CogneeClient.CogneeRelationRecord r : relations) {
                    sb.append("- ").append(r.source())
                            .append(" → (").append(r.relation()).append(") → ")
                            .append(r.target()).append("\n");
                }
            }

            log.info("[CogneeMemoryTool] recallMemory: query='{}', fragments={}, entities={}, relations={}",
                    query,
                    fragments != null ? fragments.size() : 0,
                    entities != null ? entities.size() : 0,
                    relations != null ? relations.size() : 0);

            return sb.toString().trim();

        } catch (Exception e) {
            log.error("[CogneeMemoryTool] recallMemory failed: {}", e.getMessage());
            return "记忆召回失败：" + e.getMessage();
        }
    }

    @Tool("""
            将信息保存到长期记忆中。
            输入要记忆的内容文本，系统会将其存入知识图谱，后续可通过 recallMemory 搜索到。
            适用于：当用户明确要求「记住」某些信息、保存重要知识、或记录个人偏好时调用。
            注意：保存的内容会经过语义化处理，提取实体和关系，存入永久知识图谱。
            """)
    public String saveMemory(String content) {
        if (content == null || content.isBlank()) {
            return "错误：记忆内容不能为空";
        }

        try {
            boolean success = cogneeClient.remember(content, null, true);

            if (success) {
                int charLen = content.length();
                log.info("[CogneeMemoryTool] saveMemory: {} chars saved", charLen);
                return "已成功保存到长期记忆（" + charLen + " 字符）。后续可通过「回忆记忆」搜索到相关内容。";
            } else {
                log.warn("[CogneeMemoryTool] saveMemory returned false (cognee disabled or error)");
                return "保存失败：Cognee 记忆服务不可用，请检查配置。";
            }

        } catch (Exception e) {
            log.error("[CogneeMemoryTool] saveMemory failed: {}", e.getMessage());
            return "记忆保存失败：" + e.getMessage();
        }
    }

    @Tool("""
            列出长期记忆中已知的实体和概念。
            返回知识图谱中所有已记忆的实体名称列表，帮助了解当前系统记住了哪些信息。
            适用于：当用户想了解自己有哪些已保存的记忆、或不确定记忆中有什么内容时调用。
            """)
    public String listMemories() {
        try {
            CogneeClient.GraphResponse graph = cogneeClient.getGraph();

            if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
                log.info("[CogneeMemoryTool] No entities in knowledge graph");
                return "长期记忆中暂无已保存的实体。可以通过主动对话或使用「保存记忆」功能来添加内容。";
            }

            int totalNodes = graph.getTotalNodes();
            List<CogneeClient.GraphNode> nodes = graph.getNodes();

            StringBuilder sb = new StringBuilder();
            sb.append("长期记忆中已知的实体（共 ").append(totalNodes > 0 ? totalNodes : nodes.size()).append(" 个）：\n");

            // 按类型分组
            var grouped = nodes.stream()
                    .collect(Collectors.groupingBy(
                            n -> n.type() != null && !n.type().isBlank() ? n.type() : "未分类",
                            Collectors.toList()));

            for (var entry : grouped.entrySet()) {
                sb.append("\n【").append(entry.getKey()).append("】\n");
                for (CogneeClient.GraphNode node : entry.getValue()) {
                    sb.append("- ").append(node.name()).append("\n");
                }
            }

            // 关系统计
            if (graph.getEdges() != null && !graph.getEdges().isEmpty()) {
                sb.append("\n关联关系共 ").append(graph.getEdges().size()).append(" 条。");
            }

            log.info("[CogneeMemoryTool] listMemories: {} entities, {} edges",
                    nodes.size(),
                    graph.getEdges() != null ? graph.getEdges().size() : 0);

            return sb.toString().trim();

        } catch (Exception e) {
            log.error("[CogneeMemoryTool] listMemories failed: {}", e.getMessage());
            return "获取记忆列表失败：" + e.getMessage();
        }
    }
}