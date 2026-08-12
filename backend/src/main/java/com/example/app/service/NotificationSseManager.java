package com.example.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通知 SSE 管理器（独立于聊天 SSE）。
 *
 * 专用于持久通知连接，与聊天 SSE 完全隔离：
 * <ul>
 *   <li>支持同一用户多个 SSE 连接（多标签页）</li>
 *   <li>每 30 秒发送心跳保活</li>
 *   <li>线程安全（CopyOnWriteArrayList + ConcurrentHashMap）</li>
 * </ul>
 */
@Service
@Slf4j
public class NotificationSseManager {

    private final ObjectMapper objectMapper;

    /** userId → List<SseEmitter>（支持多标签页） */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationSseManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 为用户注册一个通知 SSE emitter。
     */
    public void register(String userId, SseEmitter emitter) {
        if (userId == null || emitter == null) return;

        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            log.debug("[NotificationSse] Emitter timeout for user {}", userId);
        });
        emitter.onError(e -> {
            removeEmitter(userId, emitter);
            log.debug("[NotificationSse] Emitter error for user {}: {}", userId, e.getMessage());
        });
        emitter.onCompletion(() -> {
            removeEmitter(userId, emitter);
        });

        log.debug("[NotificationSse] Registered emitter for user {} (total: {})",
                userId, emitters.getOrDefault(userId, List.of()).size());
    }

    /**
     * 注销用户的所有 emitter（不在 register 回调中使用）。
     */
    public void unregisterAll(String userId) {
        List<SseEmitter> list = emitters.remove(userId);
        if (list != null) {
            for (SseEmitter e : list) {
                e.complete();
            }
        }
    }

    /**
     * 向指定用户的所有连接推送通知。
     */
    public void push(String userId, String event, Object data) {
        if (userId == null) return;

        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            log.debug("[NotificationSse] No active emitter for user {}", userId);
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "type", event,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            );
            String json = objectMapper.writeValueAsString(payload);

            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(event)
                            .data(json));
                } catch (IOException e) {
                    log.debug("[NotificationSse] Failed to push to an emitter for user {}: {}", userId, e.getMessage());
                    removeEmitter(userId, emitter);
                }
            }
            log.debug("[NotificationSse] Pushed {} to user {} ({} emitters)", event, userId, list.size());
        } catch (Exception e) {
            log.warn("[NotificationSse] Failed to serialize payload for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * 每 30 秒发送心跳，防止连接超时。
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        for (Map.Entry<String, List<SseEmitter>> entry : emitters.entrySet()) {
            String userId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                } catch (IOException e) {
                    removeEmitter(userId, emitter);
                }
            }
        }
    }

    public boolean hasActiveConnection(String userId) {
        List<SseEmitter> list = emitters.get(userId);
        return list != null && !list.isEmpty();
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId, list);
            }
        }
    }
}
