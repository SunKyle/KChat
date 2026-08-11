package com.example.app.service.tool.tools;

import com.example.app.dto.CreateNoteRequest;
import com.example.app.dto.NoteDTO;
import com.example.app.service.NoteService;
import com.example.app.service.tool.ToolComponent;
import com.example.app.service.tool.UserContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 笔记工具
 *
 * 暴露笔记相关工具方法，供 LLM 在 Agent 模式下管理用户笔记。
 * 复用 {@link NoteService} 的 CRUD 能力，userId 通过
 * {@link UserContextHolder} 获取。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NoteTool implements ToolComponent {

    private final NoteService noteService;

    @Tool("创建一条新笔记。当用户要求记录笔记时调用此工具。")
    String createNote(
            @P("笔记标题") String title,
            @P("笔记正文内容") String content,
            @P("分类，可选，默认为'默认'") String category,
            @P("是否置顶，可选，默认为 false") Boolean pinned) {
        String userId = UserContextHolder.get();
        log.info("[NoteTool] createNote: userId={}, title='{}'", userId, title);

        CreateNoteRequest.CreateNoteRequestBuilder builder = CreateNoteRequest.builder()
                .title(title)
                .content(content);
        if (category != null && !category.isBlank()) {
            builder.category(category);
        }
        if (pinned != null) {
            builder.pinned(pinned);
        }

        try {
            NoteDTO note = noteService.createNote(userId, builder.build());
            return "笔记创建成功。ID: " + note.getId() + "，标题: " + note.getTitle();
        } catch (Exception e) {
            log.error("[NoteTool] createNote failed", e);
            return "笔记创建失败：" + e.getMessage();
        }
    }

    @Tool("列出当前用户的所有笔记。当用户想查看笔记列表时调用。")
    String listNotes() {
        String userId = UserContextHolder.get();
        log.info("[NoteTool] listNotes: userId={}", userId);

        try {
            List<NoteDTO> notes = noteService.getAllNotes(userId);
            return formatNoteList("所有笔记", notes);
        } catch (Exception e) {
            log.error("[NoteTool] listNotes failed", e);
            return "查询笔记列表失败：" + e.getMessage();
        }
    }

    @Tool("按关键词搜索笔记。当用户想查找包含特定内容的笔记时调用。")
    String searchNotes(@P("搜索关键词") String keyword) {
        String userId = UserContextHolder.get();
        log.info("[NoteTool] searchNotes: userId={}, keyword='{}'", userId, keyword);

        try {
            List<NoteDTO> notes = noteService.searchNotes(userId, keyword);
            return formatNoteList("搜索「" + keyword + "」", notes);
        } catch (Exception e) {
            log.error("[NoteTool] searchNotes failed", e);
            return "搜索笔记失败：" + e.getMessage();
        }
    }

    @Tool("删除指定 ID 的笔记。需要先通过 listNotes 或 searchNotes 获取笔记 ID。")
    String deleteNote(@P("要删除的笔记 ID") String noteId) {
        String userId = UserContextHolder.get();
        log.info("[NoteTool] deleteNote: userId={}, noteId={}", userId, noteId);

        try {
            noteService.deleteNote(userId, noteId);
            return "笔记已删除。ID: " + noteId;
        } catch (Exception e) {
            log.error("[NoteTool] deleteNote failed", e);
            return "删除笔记失败：" + e.getMessage();
        }
    }

    /** 把笔记列表格式化为 LLM 易读的文本。 */
    private String formatNoteList(String title, List<NoteDTO> notes) {
        if (notes == null || notes.isEmpty()) {
            return title + "：没有找到任何笔记。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("，共 ").append(notes.size()).append(" 条：\n\n");
        for (int i = 0; i < notes.size(); i++) {
            NoteDTO n = notes.get(i);
            sb.append(i + 1).append(". [ID: ").append(n.getId()).append("] ");
            sb.append(n.getTitle());
            if (n.getCategory() != null) {
                sb.append(" | 分类: ").append(n.getCategory());
            }
            if (Boolean.TRUE.equals(n.getPinned())) {
                sb.append(" | 已置顶");
            }
            sb.append("\n");
            if (n.getContent() != null && !n.getContent().isBlank()) {
                String preview = n.getContent().length() > 80
                        ? n.getContent().substring(0, 80) + "..."
                        : n.getContent();
                sb.append("   内容: ").append(preview).append("\n");
            }
        }
        return sb.toString();
    }
}
