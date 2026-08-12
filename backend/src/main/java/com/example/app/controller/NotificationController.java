package com.example.app.controller;

import com.example.app.service.NotificationSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 通知控制器
 *
 * 提供 SSE 端点供前端建立持久连接，接收后端推送的通知事件。
 * 使用 {@link NotificationSseManager} 独立管理通知 emitter，
 * 与聊天 SSE 完全隔离，避免连接相互覆盖。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationController {

    private final NotificationSseManager notificationSseManager;

    /**
     * 建立 SSE 连接，接收通知推送。
     *
     * @param userId 用户 ID
     * @return SSE Emitter
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@RequestParam String userId) {
        // 超时时间设为 0 表示不超时，由心跳保活
        SseEmitter emitter = new SseEmitter(0L);

        // register 内部已设置 onTimeout/onError/onCompletion 回调，
        // emitter 结束时会自动从 manager 中移除
        notificationSseManager.register(userId, emitter);
        log.info("[Notification] SSE stream started for user {}", userId);

        return emitter;
    }
}
