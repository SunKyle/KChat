package com.example.app.service.tool.tools;

import com.example.app.dto.CreateKnowledgeBaseRequest;
import com.example.app.dto.KnowledgeBaseDTO;
import com.example.app.dto.KnowledgeDocumentDTO;
import com.example.app.service.CogneeClient;
import com.example.app.service.KnowledgeBaseService;
import com.example.app.service.tool.ToolComponent;
import com.example.app.service.tool.UserContextHolder;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库管理工具。
 *
 * <p>将 {@link KnowledgeBaseService} 的 CRUD + 检索能力以 {@code @Tool}
 * 形式暴露给 Agent。让 LLM 可以主动：
 * <ul>
 *   <li>列出/创建知识库</li>
 *   <li>上传/删除文档</li>
 *   <li>在指定知识库内检索（searchInKb）或跨所有库检索（searchAllKb）</li>
 *   <li>查询文档入库状态</li>
 * </ul>
 *
 * <p><b>和 KnowledgeBaseRetrievalStage 的关系</b>：
 * <ul>
 *   <li>用户显式 @ 知识库 → PREPROCESS 已把片段注入 system prompt，
 *       ToolDefinitionStage 会过滤掉 {@code searchInKb/searchAllKb}，避免重复检索。</li>
 *   <li>用户没有显式 @ → LLM 可以通过本工具主动查库，补足上下文。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseTool implements ToolComponent {

    private final KnowledgeBaseService kbService;
    private final CogneeClient cogneeClient;

    // ──────────────── 查询类工具 ───────────────────────────────

    @Tool("""
            在指定知识库内搜索与查询相关的内容。
            输入知识库ID和自然语言查询，返回语义相关的文档片段（带相似度、来源文档名）。
            适用于：当用户需要从特定知识库里查资料时调用，例如「帮我查设计库里关于RAG的资料」。
            注意：知识库ID是UUID格式（如 01510f33-...），不要加 kb_ 前缀。
            调用前如不知道知识库ID，请先用 listKb 或 listKbDocuments 确认。
            """)
    public String searchInKb(String kbId, String query, Integer topK) {
        if (kbId == null || kbId.isBlank()) return "错误：知识库ID不能为空";
        if (query == null || query.isBlank()) return "错误：查询内容不能为空";

        String userId = UserContextHolder.get();
        int k = (topK != null && topK > 0) ? Math.min(topK, 20) : 10;

        try {
            // 校验权限（kbService.getById 无权限抛 IllegalArgumentException）
            KnowledgeBaseDTO kb = kbService.getById(userId, kbId);

            List<CogneeClient.RecallResult> results = cogneeClient.recallFromDatasets(
                    query, k, List.of(kb.getDatasetName()));

            if (results == null || results.isEmpty()) {
                return "在知识库「" + kb.getName() + "」中未找到与「" + query + "」相关的内容。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("在知识库「").append(kb.getName()).append("」中找到 ")
                    .append(results.size()).append(" 条相关内容：\n\n");
            for (int i = 0; i < results.size(); i++) {
                CogneeClient.RecallResult r = results.get(i);
                sb.append(i + 1).append(". [相似度 ").append(String.format("%.2f", r.score()))
                        .append("]");
                if (r.documentName() != null && !r.documentName().isBlank()) {
                    sb.append("（文档: ").append(r.documentName()).append("）");
                }
                sb.append("\n   ").append(r.text()).append("\n\n");
            }

            log.info("[KnowledgeBaseTool] searchInKb: kb={}, query='{}', results={}",
                    kbId, query, results.size());
            return sb.toString().trim();

        } catch (IllegalArgumentException e) {
            return "搜索失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[KnowledgeBaseTool] searchInKb failed: kb={}, query='{}'", kbId, query, e);
            return "搜索失败：" + e.getMessage();
        }
    }

    @Tool("""
            跨用户所有知识库搜索与查询相关的内容。
            输入自然语言查询，返回语义相关的文档片段（标注来自哪个知识库、哪个文档）。
            适用于：当不确定信息在哪个库里，或需要全局搜索时调用，例如「全局搜一下架构相关资料」。
            """)
    public String searchAllKb(String query, Integer topK) {
        if (query == null || query.isBlank()) return "错误：查询内容不能为空";

        String userId = UserContextHolder.get();
        int k = (topK != null && topK > 0) ? Math.min(topK, 30) : 15;

        try {
            List<KnowledgeBaseDTO> allKb = kbService.listByUser(userId);
            if (allKb == null || allKb.isEmpty()) {
                return "你还没有任何知识库，先用 createKb 创建一个吧。";
            }

            List<String> datasetNames = allKb.stream()
                    .map(KnowledgeBaseDTO::getDatasetName)
                    .toList();

            // datasetName → 知识库名映射（给结果打标签）
            var kbNameByDataset = allKb.stream()
                    .collect(Collectors.toMap(
                            KnowledgeBaseDTO::getDatasetName,
                            KnowledgeBaseDTO::getName));

            List<CogneeClient.RecallResult> results = cogneeClient.recallFromDatasets(
                    query, k, datasetNames);

            if (results == null || results.isEmpty()) {
                return "在你的所有知识库（" + allKb.size() + " 个）中未找到与「"
                        + query + "」相关的内容。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("跨 ").append(allKb.size()).append(" 个知识库找到 ")
                    .append(results.size()).append(" 条相关内容：\n\n");
            for (int i = 0; i < results.size(); i++) {
                CogneeClient.RecallResult r = results.get(i);
                sb.append(i + 1).append(". [相似度 ").append(String.format("%.2f", r.score()))
                        .append("]");
                String kbName = r.datasetName() != null
                        ? kbNameByDataset.getOrDefault(r.datasetName(), "未知库")
                        : "未知库";
                sb.append("（知识库: ").append(kbName);
                if (r.documentName() != null && !r.documentName().isBlank()) {
                    sb.append(" · 文档: ").append(r.documentName());
                }
                sb.append("）\n   ").append(r.text()).append("\n\n");
            }

            log.info("[KnowledgeBaseTool] searchAllKb: user={}, query='{}', kbs={}, results={}",
                    userId, query, allKb.size(), results.size());
            return sb.toString().trim();

        } catch (Exception e) {
            log.error("[KnowledgeBaseTool] searchAllKb failed: query='{}'", query, e);
            return "全局搜索失败：" + e.getMessage();
        }
    }

    @Tool("""
            列出当前用户的所有知识库。
            返回每个知识库的ID、名称、描述、文档数量和更新时间。
            适用于：当用户想查看自己有哪些知识库、或需要获取知识库ID供其他工具使用时调用。
            """)
    public String listKb() {
        String userId = UserContextHolder.get();
        try {
            List<KnowledgeBaseDTO> kbs = kbService.listByUser(userId);
            if (kbs == null || kbs.isEmpty()) {
                return "你还没有任何知识库。可以使用 createKb 创建一个新知识库。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("你共有 ").append(kbs.size()).append(" 个知识库：\n\n");
            for (int i = 0; i < kbs.size(); i++) {
                KnowledgeBaseDTO kb = kbs.get(i);
                sb.append(i + 1).append(". 【").append(kb.getName()).append("】")
                        .append("（ID: ").append(kb.getId())
                        .append("，UUID格式，调用时不要加 kb_ 前缀）\n");
                if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
                    sb.append("   描述: ").append(kb.getDescription()).append("\n");
                }
                sb.append("   文档数: ").append(kb.getDocumentCount() != null ? kb.getDocumentCount() : 0);
                if (kb.getUpdatedAt() != null) {
                    sb.append(" · 更新于: ").append(kb.getUpdatedAt().toLocalDate());
                }
                sb.append("\n\n");
            }

            log.info("[KnowledgeBaseTool] listKb: user={}, count={}", userId, kbs.size());
            return sb.toString().trim();

        } catch (Exception e) {
            log.error("[KnowledgeBaseTool] listKb failed: user={}", userId, e);
            return "获取知识库列表失败：" + e.getMessage();
        }
    }

    @Tool("""
            列出指定知识库下的所有文档清单。
            输入知识库ID，返回每个文档的ID、文件名、处理状态、大小和创建时间。
            适用于：当用户想查看某个知识库里有哪些文档、或需要获取文档ID来删除/查询状态时调用。
            注意：知识库ID是UUID格式（如 01510f33-...），不要加 kb_ 前缀。
            如不知道知识库ID，请先用 listKb 确认。
            """)
    public String listKbDocuments(String kbId) {
        if (kbId == null || kbId.isBlank()) return "错误：知识库ID不能为空";
        String userId = UserContextHolder.get();

        try {
            KnowledgeBaseDTO kb = kbService.getById(userId, kbId);
            List<KnowledgeDocumentDTO> docs = kbService.listDocuments(userId, kbId);

            if (docs == null || docs.isEmpty()) {
                return "知识库「" + kb.getName() + "」下暂无文档，可以使用 uploadKbDocument 添加。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("知识库「").append(kb.getName()).append("」下共有 ")
                    .append(docs.size()).append(" 个文档：\n\n");
            for (int i = 0; i < docs.size(); i++) {
                KnowledgeDocumentDTO d = docs.get(i);
                sb.append(i + 1).append(". ").append(d.getFileName())
                        .append("（ID: ").append(d.getId()).append("）\n");
                sb.append("   状态: ").append(formatStatus(d.getStatus()));
                if (d.getContentLength() != null) {
                    sb.append(" · 字符数: ").append(d.getContentLength());
                }
                if (d.getCreatedAt() != null) {
                    sb.append(" · 创建于: ").append(d.getCreatedAt().toLocalDate());
                }
                sb.append("\n");
            }

            log.info("[KnowledgeBaseTool] listKbDocuments: kb={}, docs={}", kbId, docs.size());
            return sb.toString().trim();

        } catch (IllegalArgumentException e) {
            return "获取文档列表失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[KnowledgeBaseTool] listKbDocuments failed: kb={}", kbId, e);
            return "获取文档列表失败：" + e.getMessage();
        }
    }

    @Tool("""
            查询知识库中指定文档的入库处理状态。
            输入知识库ID和文档ID，返回处理状态（处理中/已入库/失败）、错误信息和字符数。
            适用于：刚上传完文档想确认是否已完成索引时调用。
            """)
    public String getKbDocumentStatus(String kbId, String documentId) {
        if (kbId == null || kbId.isBlank()) return "错误：知识库ID不能为空";
        if (documentId == null || documentId.isBlank()) return "错误：文档ID不能为空";
        String userId = UserContextHolder.get();

        try {
            KnowledgeDocumentDTO d = kbService.getDocumentStatus(userId, kbId, documentId);
            StringBuilder sb = new StringBuilder();
            sb.append("文档「").append(d.getFileName()).append("」的状态：\n");
            sb.append("  处理状态: ").append(formatStatus(d.getStatus())).append("\n");
            sb.append("  字符数: ").append(d.getContentLength() != null ? d.getContentLength() : 0).append("\n");
            if (d.getStatus() != null && d.getStatus().equalsIgnoreCase("FAILED")
                    && d.getErrorMessage() != null && !d.getErrorMessage().isBlank()) {
                sb.append("  错误原因: ").append(d.getErrorMessage()).append("\n");
            }
            return sb.toString().trim();

        } catch (IllegalArgumentException e) {
            return "查询状态失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[KnowledgeBaseTool] getKbDocumentStatus failed: kb={}, doc={}", kbId, documentId, e);
            return "查询状态失败：" + e.getMessage();
        }
    }

    // ──────────────── 变更类工具 ───────────────────────────────

    @Tool("""
            创建一个新的知识库。
            输入知识库名称和可选描述，返回新知识库的ID和基础信息。
            适用于：当用户需要新建一个知识库来分类存储文档时调用，例如「帮我建一个项目笔记库」。
            """)
    public String createKb(String name, String description) {
        if (name == null || name.isBlank()) return "错误：知识库名称不能为空";
        if (name.length() > 200) return "错误：知识库名称不能超过200字符";
        if (description != null && description.length() > 1000) {
            return "错误：描述不能超过1000字符";
        }
        String userId = UserContextHolder.get();

        try {
            CreateKnowledgeBaseRequest req = CreateKnowledgeBaseRequest.builder()
                    .name(name.trim())
                    .description(description != null ? description.trim() : null)
                    .build();
            KnowledgeBaseDTO kb = kbService.create(userId, req);

            StringBuilder sb = new StringBuilder();
            sb.append("✅ 已创建知识库：\n");
            sb.append("  名称: ").append(kb.getName()).append("\n");
            sb.append("  ID: ").append(kb.getId()).append("\n");
            if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
                sb.append("  描述: ").append(kb.getDescription()).append("\n");
            }
            sb.append("\n接下来可以用 uploadKbDocument 把内容存进去，或用 listKb 查看所有库。");
            return sb.toString().trim();

        } catch (Exception e) {
            log.error("[KnowledgeBaseTool] createKb failed: user={}, name='{}'", userId, name, e);
            return "创建知识库失败：" + e.getMessage();
        }
    }

    @Tool("""
            把一段纯文本内容作为文档上传到指定知识库。
            输入：知识库ID、文件名（任意名称用于展示，可选）、文档正文内容。
            系统会将内容写入数据库，并异步索引到 Cognee 知识图谱（稍后可被检索）。
            适用于：当用户要求把某段文字/笔记/文章保存到知识库时调用。
            注意：知识库ID是UUID格式（如 01510f33-...），不要加 kb_ 前缀。
            如不知道知识库ID，请先用 listKb 确认。
            """)
    public String uploadKbDocument(String kbId, String fileName, String content) {
        if (kbId == null || kbId.isBlank()) return "错误：知识库ID不能为空";
        if (content == null || content.isBlank()) return "错误：文档内容不能为空";
        String userId = UserContextHolder.get();

        try {
            KnowledgeDocumentDTO doc = kbService.uploadTextDocument(
                    userId, kbId, fileName, content);

            StringBuilder sb = new StringBuilder();
            sb.append("✅ 已上传文档到知识库：\n");
            sb.append("  文件名: ").append(doc.getFileName()).append("\n");
            sb.append("  文档ID: ").append(doc.getId()).append("\n");
            sb.append("  字符数: ").append(doc.getContentLength() != null ? doc.getContentLength() : 0).append("\n");
            sb.append("  状态: ").append(formatStatus(doc.getStatus()))
                    .append("（索引完成后可用 searchInKb / searchAllKb 检索）\n");
            if (doc.getStatus() != null && !doc.getStatus().equalsIgnoreCase("INDEXED")) {
                sb.append("  💡 稍后可用 getKbDocumentStatus 检查入库进度。\n");
            }
            return sb.toString().trim();

        } catch (IllegalArgumentException e) {
            return "上传文档失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[KnowledgeBaseTool] uploadKbDocument failed: kb={}, file='{}'", kbId, fileName, e);
            return "上传文档失败：" + e.getMessage();
        }
    }

    @Tool("""
            删除指定知识库中的一个文档。
            输入知识库ID和文档ID，删除后会自动重新索引该知识库的 Cognee 数据集。
            适用于：当用户要求删除知识库里某份文档时调用。
            如不知道文档ID，请先用 listKbDocuments 确认。
            注意：删除操作不可恢复，请先确认用户意图。
            """)
    public String deleteKbDocument(String kbId, String documentId) {
        if (kbId == null || kbId.isBlank()) return "错误：知识库ID不能为空";
        if (documentId == null || documentId.isBlank()) return "错误：文档ID不能为空";
        String userId = UserContextHolder.get();

        try {
            kbService.deleteDocument(userId, kbId, documentId);
            log.info("[KnowledgeBaseTool] deleteKbDocument: kb={}, doc={}", kbId, documentId);
            return "✅ 已删除文档（ID: " + documentId + "）。\n"
                    + "知识库的 Cognee 索引正在后台重建，稍候检索结果会自动更新。";

        } catch (IllegalArgumentException e) {
            return "删除文档失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[KnowledgeBaseTool] deleteKbDocument failed: kb={}, doc={}", kbId, documentId, e);
            return "删除文档失败：" + e.getMessage();
        }
    }

    // ──────────────── 辅助方法 ───────────────────────────────

    private static String formatStatus(String status) {
        if (status == null || status.isBlank()) return "未知";
        return switch (status.toUpperCase()) {
            case "PROCESSING" -> "⏳ 处理中";
            case "INDEXED" -> "✅ 已入库";
            case "FAILED" -> "❌ 失败";
            default -> status;
        };
    }
}
