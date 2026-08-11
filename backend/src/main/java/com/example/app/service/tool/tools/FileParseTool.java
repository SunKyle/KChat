package com.example.app.service.tool.tools;

import com.example.app.service.FileService;
import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 文件解析工具
 *
 * 暴露文件解析相关工具方法，供 LLM 在 Agent 模式下解析用户上传的文档。
 * 复用 {@link FileService} 的 Tika 解析能力，支持
 * PDF / Word / Excel / PowerPoint / HTML / TXT / Markdown / CSV / JSON 等格式。
 *
 * <p>典型场景：用户上传文件后，前端将 fileId 附加到消息上下文，
 * LLM 识别到 fileId 后调用 {@code parseFile} 提取文本内容，再基于内容回答用户问题。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileParseTool implements ToolComponent {

    private final FileService fileService;

    @Tool("解析指定 ID 的文件，提取文本内容。支持 PDF、Word(.doc/.docx)、Excel(.xls/.xlsx)、PowerPoint(.ppt/.pptx)、TXT、Markdown、HTML、CSV、JSON 等格式。当用户提到已上传的文件或需要分析文件内容时调用。")
    String parseFile(@P("文件 ID（用户上传文件后获得的 fileId）") String fileId) {
        log.info("[FileParseTool] parseFile: fileId={}", fileId);

        if (fileId == null || fileId.isBlank()) {
            return "文件 ID 不能为空。";
        }

        try {
            String content = fileService.parseFile(fileId);
            if (content == null || content.isBlank()) {
                return "文件解析完成，但未提取到任何文本内容。可能是二进制文件或扫描件。";
            }

            // 返回格式化结果，附带文件信息供 LLM 参考
            Map<String, Object> info = fileService.getFileInfo(fileId);
            StringBuilder sb = new StringBuilder();
            sb.append("文件解析成功。\n");
            sb.append("文件名: ").append(info.get("fileName")).append("\n");
            sb.append("大小: ").append(info.get("size")).append(" bytes\n");
            sb.append("类型: ").append(info.get("contentType")).append("\n");
            sb.append("--- 文件内容 ---\n");
            sb.append(content);
            if (content.length() >= 8000) {
                sb.append("\n--- 内容已截断（最多 8000 字符），如需完整内容请分段询问 ---");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[FileParseTool] parseFile failed: fileId={}", fileId, e);
            return "文件解析失败: " + e.getMessage();
        }
    }

    @Tool("列出所有已上传的文件。当不确定有哪些文件可用时调用，返回文件 ID、名称和大小。")
    String listFiles() {
        log.info("[FileParseTool] listFiles");

        try {
            List<Map<String, Object>> files = fileService.listFiles();
            if (files.isEmpty()) {
                return "当前没有已上传的文件。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("已上传文件，共 ").append(files.size()).append(" 个：\n\n");
            for (int i = 0; i < files.size(); i++) {
                Map<String, Object> f = files.get(i);
                sb.append(i + 1).append(". [ID: ").append(f.get("fileId")).append("] ");
                sb.append(f.get("fileName"));
                sb.append(" (").append(f.get("size")).append(" bytes)\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[FileParseTool] listFiles failed", e);
            return "查询文件列表失败: " + e.getMessage();
        }
    }
}
