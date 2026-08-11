package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 定时提醒实体
 *
 * 供 Agent 的 setReminder 工具创建定时提醒。
 * 系统每分钟检查一次，到达提醒时间时通过 SSE 推送通知给用户。
 */
@Entity
@Table(name = "reminders", indexes = {
    @Index(name = "idx_reminders_user_id", columnList = "user_id"),
    @Index(name = "idx_reminders_status", columnList = "status"),
    @Index(name = "idx_reminders_remind_at", columnList = "remind_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reminder {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 提醒时间 */
    @Column(name = "remind_at", nullable = false)
    private LocalDateTime remindAt;

    /** 状态：pending(待触发) / fired(已触发) / cancelled(已取消) */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "fired_at")
    private LocalDateTime firedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "pending";
        }
    }
}