package com.example.app.service.tool.tools;

import com.example.app.entity.Reminder;
import com.example.app.service.ReminderService;
import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 定时提醒工具
 *
 * 供 LLM 在 Agent 模式下为用户创建定时提醒。
 * 支持：创建提醒、列出提醒、取消提醒。
 * 到期时通过 SSE 推送通知给用户。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SetReminderTool implements ToolComponent {

    private final ReminderService reminderService;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool("""
            创建一个定时提醒。指定提醒标题和触发时间，系统会在指定时间推送通知。
            时间格式为 yyyy-MM-dd HH:mm:ss（24 小时制，时区 Asia/Shanghai）。
            例如：创建会议提醒，时间为 2025-08-12 14:30:00
            """)
    public String createReminder(String title, String remindAt) {
        if (title == null || title.isBlank()) {
            return "错误：提醒标题不能为空";
        }
        if (remindAt == null || remindAt.isBlank()) {
            return "错误：提醒时间不能为空，格式应为 yyyy-MM-dd HH:mm:ss";
        }

        try {
            Reminder reminder = reminderService.createReminder(title, remindAt);
            return "提醒已创建成功！\n📌 标题：" + reminder.getTitle()
                    + "\n⏰ 时间：" + reminder.getRemindAt().format(FORMATTER)
                    + "\n🆔 编号：" + reminder.getId();
        } catch (IllegalArgumentException e) {
            return "创建失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[SetReminder] Failed to create reminder: {}", e.getMessage());
            return "创建失败：" + e.getMessage();
        }
    }

    @Tool("""
            列出当前用户的所有提醒（包括待触发和已触发）。
            返回提醒标题、时间和状态。
            """)
    public String listReminders() {
        String userId = com.example.app.service.tool.UserContextHolder.get();
        if (userId == null) userId = "default";

        List<Reminder> reminders = reminderService.listReminders(userId);
        if (reminders.isEmpty()) {
            return "暂无提醒事项。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("您共有 ").append(reminders.size()).append(" 条提醒：\n\n");
        for (Reminder r : reminders) {
            String statusIcon = switch (r.getStatus()) {
                case "pending" -> "⏳";
                case "fired" -> "✅";
                case "cancelled" -> "❌";
                default -> "📌";
            };
            sb.append(statusIcon).append(" [").append(r.getStatus()).append("] ")
                    .append(r.getTitle())
                    .append("\n   时间：").append(r.getRemindAt().format(FORMATTER))
                    .append("\n   编号：").append(r.getId())
                    .append("\n\n");
        }
        return sb.toString();
    }

    @Tool("""
            取消一个待触发的提醒。需要提供提醒编号（ID），可通过 listReminders 获取。
            """)
    public String cancelReminder(String reminderId) {
        if (reminderId == null || reminderId.isBlank()) {
            return "错误：提醒编号不能为空，请通过 listReminders 获取";
        }

        String userId = com.example.app.service.tool.UserContextHolder.get();
        if (userId == null) userId = "default";

        boolean cancelled = reminderService.cancelReminder(reminderId, userId);
        if (cancelled) {
            return "提醒已取消。";
        }
        return "取消失败：找不到该提醒，或提醒已被触发/取消。";
    }
}