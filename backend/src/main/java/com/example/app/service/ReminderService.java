package com.example.app.service;

import com.example.app.entity.Reminder;
import com.example.app.repository.ReminderRepository;
import com.example.app.service.tool.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * 提醒服务
 *
 * 提供定时提醒的创建、查询、完成和删除能力。
 * 通过 Spring @Scheduled 每分钟检查一次到期提醒，
 * 到期时通过 {@link NotificationService} 推送 SSE 通知给用户。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final NotificationService notificationService;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 创建提醒。
     *
     * @param title    提醒标题
     * @param remindAt 提醒时间（格式：yyyy-MM-dd HH:mm:ss）
     * @return 创建的提醒
     */
    @Transactional
    public Reminder createReminder(String title, String remindAt) {
        String userId = UserContextHolder.get();
        if (userId == null) {
            userId = "default";
        }

        LocalDateTime time;
        try {
            time = LocalDateTime.parse(remindAt, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式错误，应为 yyyy-MM-dd HH:mm:ss 格式");
        }

        if (time.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("提醒时间不能早于当前时间");
        }

        Reminder reminder = Reminder.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .title(title)
                .remindAt(time)
                .status("pending")
                .build();

        reminderRepository.save(reminder);
        log.info("[Reminder] Created reminder: id={}, user={}, title='{}', at={}",
                reminder.getId(), userId, title, remindAt);
        return reminder;
    }

    /**
     * 列出用户的所有提醒。
     */
    public List<Reminder> listReminders(String userId) {
        if (userId == null) {
            userId = "default";
        }
        return reminderRepository.findByUserIdOrderByRemindAtAsc(userId);
    }

    /**
     * 列出用户的待触发提醒。
     */
    public List<Reminder> listPendingReminders(String userId) {
        if (userId == null) {
            userId = "default";
        }
        return reminderRepository.findByUserIdAndStatusOrderByRemindAtAsc(userId, "pending");
    }

    /**
     * 取消提醒。
     */
    @Transactional
    public boolean cancelReminder(String id, String userId) {
        if (userId == null) {
            userId = "default";
        }
        int updated = reminderRepository.cancelByIdAndUserId(id, userId);
        if (updated > 0) {
            log.info("[Reminder] Cancelled reminder: id={}, user={}", id, userId);
            return true;
        }
        return false;
    }

    /**
     * 标记提醒为已触发。
     */
    @Transactional
    public void markAsFired(String id) {
        Reminder reminder = reminderRepository.findById(id).orElse(null);
        if (reminder != null && "pending".equals(reminder.getStatus())) {
            reminder.setStatus("fired");
            reminder.setFiredAt(LocalDateTime.now());
            reminderRepository.save(reminder);
        }
    }

    /**
     * 每分钟检查到期提醒并触发。
     */
    @Scheduled(fixedRate = 60000)
    public void checkAndFireDueReminders() {
        List<Reminder> due = reminderRepository.findDueReminders(LocalDateTime.now());
        if (due.isEmpty()) {
            return;
        }

        log.info("[Reminder] Found {} due reminder(s)", due.size());
        for (Reminder reminder : due) {
            try {
                markAsFired(reminder.getId());
                notificationService.pushNotification(
                        reminder.getUserId(),
                        "reminder",
                        buildReminderMessage(reminder)
                );
                log.info("[Reminder] Fired reminder: id={}, title='{}'",
                        reminder.getId(), reminder.getTitle());
            } catch (Exception e) {
                log.error("[Reminder] Failed to fire reminder: id={}", reminder.getId(), e);
            }
        }
    }

    private String buildReminderMessage(Reminder reminder) {
        return String.format("⏰ 提醒时间到！\n📌 %s\n⏰ %s",
                reminder.getTitle(),
                reminder.getRemindAt().format(FORMATTER));
    }
}