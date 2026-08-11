package com.example.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 通知服务
 *
 * 管理按用户 ID 分组的 {@link SseEmitter} 集合，支持：
 * <ul>
 *   <li>注册/注销 emitter</li>
 *   <li>向指定用户推送通知事件</li>
 * </ul>
 *
 * <p>主要用于定时提醒等后台任务向活跃会话推送消息。
 */
@Service
@Slf4j
public class NotificationService {

    private final ObjectMapper objectMapper;

    /** userId → SseEmitter */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public NotificationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 为用户注册 SSE emitter。
     * 如果用户已有活跃 emitter，旧的会被自动关闭。
     */
    public void registerEmitter(String userId, SseEmitter emitter) {
        if (userId == null || emitter == null) return;

        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) {
            old.complete();
        }

        emitter.onTimeout(() -> {
            emitters.remove(userId, emitter);
            log.debug("[Notification] Emitter timeout for user {}", userId);
        });
        emitter.onError(e -> {
            emitters.remove(userId, emitter);
            log.debug("[Notification] Emitter error for user {}: {}", userId, e.getMessage());
        });
        emitter.onCompletion(() -> {
            emitters.remove(userId, emitter);
        });

        log.debug("[Notification] Registered emitter for user {}", userId);
    }

    /**
     * 为用户注销 SSE emitter。
     */
    public void unregisterEmitter(String userId) {
        if (userId != null) {
            SseEmitter emitter = emitters.remove(userId);
            if (emitter != null) {
                emitter.complete();
            }
        }
    }

    /**
     * 向指定用户推送通知。
     * 如果用户当前没有活跃的 SSE 会话，通知会被丢弃（提醒已在数据库中标记为 fired）。
     */
    public void pushNotification(String userId, String event, String data) {
        if (userId == null) return;

        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.debug("[Notification] No active emitter for user {}, notification queued in DB", userId);
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "type", event,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            );
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(payload)));
            log.debug("[Notification] Pushed {} to user {}", event, userId);
        } catch (IOException e) {
            log.warn("[Notification] Failed to push to user {}: {}", userId, e.getMessage());
            emitters.remove(userId);
        }
    }

    /**
     * 判断用户是否有活跃的 SSE 连接。
     */
    public boolean hasActiveConnection(String userId) {
        return userId != null && emitters.containsKey(userId);
    }
}