package com.example.app.service.tool.tools;

import com.example.app.dto.CreateTodoRequest;
import com.example.app.dto.TodoDTO;
import com.example.app.service.TodoService;
import com.example.app.service.tool.ToolComponent;
import com.example.app.service.tool.UserContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 待办事项工具
 *
 * 暴露待办相关工具方法，供 LLM 在 Agent 模式下管理用户待办。
 * 复用 {@link TodoService} 的 CRUD 能力，userId 通过
 * {@link UserContextHolder} 获取。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodoTool implements ToolComponent {

    private final TodoService todoService;

    @Tool("创建一条新待办事项。当用户要求添加待办或提醒时调用。")
    String createTodo(
            @P("待办标题") String title,
            @P("待办描述，可选") String description,
            @P("优先级：low/medium/high，可选，默认 medium") String priority,
            @P("分类，可选，默认'默认'") String category) {
        String userId = UserContextHolder.get();
        log.info("[TodoTool] createTodo: userId={}, title='{}'", userId, title);

        CreateTodoRequest.CreateTodoRequestBuilder builder = CreateTodoRequest.builder()
                .title(title);
        if (description != null && !description.isBlank()) {
            builder.description(description);
        }
        if (priority != null && !priority.isBlank()) {
            builder.priority(priority);
        }
        if (category != null && !category.isBlank()) {
            builder.category(category);
        }

        try {
            TodoDTO todo = todoService.createTodo(userId, builder.build());
            return "待办创建成功。ID: " + todo.getId() + "，标题: " + todo.getTitle()
                    + "，状态: " + todo.getStatus();
        } catch (Exception e) {
            log.error("[TodoTool] createTodo failed", e);
            return "待办创建失败：" + e.getMessage();
        }
    }

    @Tool("列出当前用户的所有待办事项。当用户想查看待办列表时调用。")
    String listTodos() {
        String userId = UserContextHolder.get();
        log.info("[TodoTool] listTodos: userId={}", userId);

        try {
            List<TodoDTO> todos = todoService.getAllTodos(userId);
            return formatTodoList("所有待办", todos);
        } catch (Exception e) {
            log.error("[TodoTool] listTodos failed", e);
            return "查询待办列表失败：" + e.getMessage();
        }
    }

    @Tool("按关键词搜索待办事项。")
    String searchTodos(@P("搜索关键词") String keyword) {
        String userId = UserContextHolder.get();
        log.info("[TodoTool] searchTodos: userId={}, keyword='{}'", userId, keyword);

        try {
            List<TodoDTO> todos = todoService.searchTodos(userId, keyword);
            return formatTodoList("搜索「" + keyword + "」", todos);
        } catch (Exception e) {
            log.error("[TodoTool] searchTodos failed", e);
            return "搜索待办失败：" + e.getMessage();
        }
    }

    @Tool("将指定待办标记为已完成。需要先通过 listTodos 或 searchTodos 获取待办 ID。")
    String completeTodo(@P("要完成的待办 ID") String todoId) {
        String userId = UserContextHolder.get();
        log.info("[TodoTool] completeTodo: userId={}, todoId={}", userId, todoId);

        try {
            TodoDTO todo = todoService.toggleTodoStatus(userId, todoId);
            return "待办状态已更新。ID: " + todo.getId() + "，当前状态: " + todo.getStatus();
        } catch (Exception e) {
            log.error("[TodoTool] completeTodo failed", e);
            return "完成待办失败：" + e.getMessage();
        }
    }

    @Tool("获取所有已过期但未完成的待办事项。当用户想查看逾期任务时调用。")
    String getOverdueTodos() {
        String userId = UserContextHolder.get();
        log.info("[TodoTool] getOverdueTodos: userId={}", userId);

        try {
            List<TodoDTO> todos = todoService.getOverdueTodos(userId);
            return formatTodoList("逾期待办", todos);
        } catch (Exception e) {
            log.error("[TodoTool] getOverdueTodos failed", e);
            return "查询逾期待办失败：" + e.getMessage();
        }
    }

    /** 把待办列表格式化为 LLM 易读的文本。 */
    private String formatTodoList(String title, List<TodoDTO> todos) {
        if (todos == null || todos.isEmpty()) {
            return title + "：没有找到任何待办。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("，共 ").append(todos.size()).append(" 条：\n\n");
        for (int i = 0; i < todos.size(); i++) {
            TodoDTO t = todos.get(i);
            sb.append(i + 1).append(". [ID: ").append(t.getId()).append("] ");
            sb.append(t.getTitle());
            sb.append(" | 状态: ").append(t.getStatus());
            sb.append(" | 优先级: ").append(t.getPriority());
            if (t.getDueDate() != null) {
                sb.append(" | 截止: ").append(t.getDueDate());
            }
            if (t.getCategory() != null) {
                sb.append(" | 分类: ").append(t.getCategory());
            }
            sb.append("\n");
            if (t.getDescription() != null && !t.getDescription().isBlank()) {
                String preview = t.getDescription().length() > 80
                        ? t.getDescription().substring(0, 80) + "..."
                        : t.getDescription();
                sb.append("   描述: ").append(preview).append("\n");
            }
        }
        return sb.toString();
    }
}
