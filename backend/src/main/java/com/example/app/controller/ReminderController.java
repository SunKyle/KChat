package com.example.app.controller;

import com.example.app.dto.CreateReminderRequest;
import com.example.app.dto.ReminderDTO;
import com.example.app.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
@Slf4j
public class ReminderController {

    private final ReminderService reminderService;

    /**
     * 获取用户的所有提醒（含待触发与历史）。
     */
    @GetMapping
    public ResponseEntity<List<ReminderDTO>> getReminders(@RequestParam String userId) {
        return ResponseEntity.ok(reminderService.getAllReminders(userId));
    }

    /**
     * 创建提醒。
     */
    @PostMapping
    public ResponseEntity<ReminderDTO> createReminder(
            @RequestParam String userId,
            @RequestBody CreateReminderRequest request) {

        ReminderDTO dto = reminderService.createReminderForUser(userId, request);
        log.info("Reminder created via API: {} for user {}", dto.getId(), userId);
        return ResponseEntity.ok(dto);
    }

    /**
     * 更新提醒（仅待触发的可修改）。
     */
    @PutMapping("/{reminderId}")
    public ResponseEntity<ReminderDTO> updateReminder(
            @RequestParam String userId,
            @PathVariable String reminderId,
            @RequestBody CreateReminderRequest request) {

        ReminderDTO dto = reminderService.updateReminderForUser(reminderId, userId, request);
        log.info("Reminder updated via API: {} for user {}", reminderId, userId);
        return ResponseEntity.ok(dto);
    }

    /**
     * 取消提醒（仅待触发的可取消）。
     */
    @DeleteMapping("/{reminderId}")
    public ResponseEntity<Void> cancelReminder(
            @RequestParam String userId,
            @PathVariable String reminderId) {

        reminderService.cancelReminderForUser(reminderId, userId);
        log.info("Reminder cancelled via API: {} for user {}", reminderId, userId);
        return ResponseEntity.noContent().build();
    }
}
