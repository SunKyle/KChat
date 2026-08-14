package com.example.app.service.tool.tools;

import com.example.app.service.tool.ToolComponent;
import com.example.app.service.tool.UserContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Markdown 文档工具
 *
 * 允许 LLM 在 Agent 模式下创建、查看、搜索 Markdown 文档。
 * 文档以 .md 文件形式存储在本地文件系统，适合生成设计稿、
 * 技术文档、会议纪要等长格式内容。
 *
 * 与 NoteTool 的区别：
 * - Note: 存储在数据库中，短文本，随手记录
 * - Document: 存储为 .md 文件，长文本，结构化文档
 */
@Component
@Slf4j
public class DocumentTool implements ToolComponent {

    @Value("${kchat.document-dir:documents}")
    private String documentDir;

    /**
     * 创建一个 Markdown 文档。
     * 当用户要求生成文档、设计稿、技术方案、会议纪要等长格式内容时调用。
     */
    @Tool("创建一个 Markdown 文档并保存为 .md 文件。当用户要求生成设计稿、技术文档、方案文档、会议纪要等时调用此工具。")
    String createDocument(
            @P("文档标题，将用作文件名") String title,
            @P("文档内容，Markdown 格式") String content) {
        String userId = UserContextHolder.get();
        log.info("[DocumentTool] createDocument: userId={}, title='{}'", userId, title);

        try {
            Path dir = getDocumentPath(userId);
            Files.createDirectories(dir);

            String fileName = sanitizeFileName(title) + ".md";
            Path filePath = dir.resolve(fileName);

            // 添加元数据头
            String fullContent = buildDocumentContent(title, content);
            Files.writeString(filePath, fullContent);

            log.info("[DocumentTool] Document created: {}", filePath);
            return "文档创建成功。文件: " + fileName + "，路径: " + filePath;
        } catch (IOException e) {
            log.error("[DocumentTool] createDocument failed", e);
            return "文档创建失败：" + e.getMessage();
        }
    }

    /**
     * 列出当前用户的所有文档。
     */
    @Tool("列出当前用户的所有 Markdown 文档。当用户想查看已有文档列表时调用。")
    String listDocuments() {
        String userId = UserContextHolder.get();
        log.info("[DocumentTool] listDocuments: userId={}", userId);

        try {
            Path dir = getDocumentPath(userId);
            if (!Files.exists(dir)) {
                return "暂无任何文档。";
            }

            try (Stream<Path> stream = Files.list(dir)) {
                List<Path> files = stream
                        .filter(p -> p.toString().endsWith(".md"))
                        .sorted((a, b) -> {
                            try {
                                return Files.getLastModifiedTime(b)
                                        .compareTo(Files.getLastModifiedTime(a));
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .collect(Collectors.toList());

                if (files.isEmpty()) {
                    return "暂无任何文档。";
                }

                StringBuilder sb = new StringBuilder();
                sb.append("共 ").append(files.size()).append(" 个文档：\n\n");
                for (int i = 0; i < files.size(); i++) {
                    Path f = files.get(i);
                    String name = f.getFileName().toString();
                    long size = Files.size(f);
                    sb.append(i + 1).append(". ").append(name);
                    sb.append(" (").append(formatSize(size)).append(")\n");
                }
                return sb.toString();
            }
        } catch (IOException e) {
            log.error("[DocumentTool] listDocuments failed", e);
            return "查询文档列表失败：" + e.getMessage();
        }
    }

    /**
     * 读取指定文档的完整内容。
     */
    @Tool("读取指定 Markdown 文档的完整内容。当用户想查看某个文档的详细内容时调用。")
    String readDocument(@P("文档文件名，如 design.md") String fileName) {
        String userId = UserContextHolder.get();
        log.info("[DocumentTool] readDocument: userId={}, file='{}'", userId, fileName);

        try {
            Path filePath = getDocumentPath(userId).resolve(sanitizeFileName(fileName));
            if (!Files.exists(filePath)) {
                return "文档不存在: " + fileName;
            }
            return Files.readString(filePath);
        } catch (IOException e) {
            log.error("[DocumentTool] readDocument failed", e);
            return "读取文档失败：" + e.getMessage();
        }
    }

    /**
     * 在所有文档中搜索关键词。
     */
    @Tool("在所有 Markdown 文档中搜索包含指定关键词的文档。当用户想查找包含特定内容的文档时调用。")
    String searchDocuments(@P("搜索关键词") String keyword) {
        String userId = UserContextHolder.get();
        log.info("[DocumentTool] searchDocuments: userId={}, keyword='{}'", userId, keyword);

        try {
            Path dir = getDocumentPath(userId);
            if (!Files.exists(dir)) {
                return "暂无任何文档可搜索。";
            }

            StringBuilder sb = new StringBuilder();
            int found = 0;

            try (Stream<Path> stream = Files.walk(dir)) {
                List<Path> files = stream
                        .filter(p -> p.toString().endsWith(".md"))
                        .collect(Collectors.toList());

                for (Path file : files) {
                    String content = Files.readString(file);
                    if (content.toLowerCase().contains(keyword.toLowerCase())) {
                        found++;
                        sb.append(found).append(". ")
                                .append(file.getFileName())
                                .append("\n");

                        // 提取包含关键词的行作为预览
                        String[] lines = content.split("\n");
                        for (String line : lines) {
                            if (line.toLowerCase().contains(keyword.toLowerCase())) {
                                String preview = line.length() > 80
                                        ? line.substring(0, 80) + "..."
                                        : line;
                                sb.append("   > ").append(preview).append("\n");
                                break;
                            }
                        }
                        sb.append("\n");
                    }
                }
            }

            if (found == 0) {
                return "未找到包含「" + keyword + "」的文档。";
            }
            return "找到 " + found + " 个匹配文档：\n\n" + sb;
        } catch (IOException e) {
            log.error("[DocumentTool] searchDocuments failed", e);
            return "搜索文档失败：" + e.getMessage();
        }
    }

    /**
     * 删除指定文档。
     */
    @Tool("删除指定的 Markdown 文档。需要先通过 listDocuments 获取文件名。")
    String deleteDocument(@P("要删除的文档文件名") String fileName) {
        String userId = UserContextHolder.get();
        log.info("[DocumentTool] deleteDocument: userId={}, file='{}'", userId, fileName);

        try {
            Path filePath = getDocumentPath(userId).resolve(sanitizeFileName(fileName));
            if (!Files.exists(filePath)) {
                return "文档不存在: " + fileName;
            }
            Files.delete(filePath);
            return "文档已删除: " + fileName;
        } catch (IOException e) {
            log.error("[DocumentTool] deleteDocument failed", e);
            return "删除文档失败：" + e.getMessage();
        }
    }

    // ── Helper methods ────────────────────────────────────────────

    private Path getDocumentPath(String userId) {
        return Paths.get(documentDir, userId);
    }

    /** 将标题转为安全的文件名 */
    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "untitled";
        }
        // 去掉已有扩展名
        if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        // 替换不安全字符
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /** 构建文档内容，添加元数据头 */
    private static String buildDocumentContent(String title, String content) {
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(title).append("\n");
        sb.append("created: ").append(timestamp).append("\n");
        sb.append("---\n\n");
        sb.append("# ").append(title).append("\n\n");
        sb.append(content);
        if (!content.endsWith("\n")) {
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }
}
