package com.example.app.aspect;

import com.example.app.dto.ContentOptimizationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 使用 Redis 实现滑动窗口限流
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${rate-limit.requests-per-minute:10}")
    private int requestsPerMinute;

    @Value("${rate-limit.cache-prefix:optimize:rate:}")
    private String cachePrefix;

    /**
     * 限流注解
     */
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    public @interface RateLimited {
    }

    /**
     * 限流环绕通知
     */
    @Around("@annotation(com.example.app.aspect.RateLimitAspect.RateLimited)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!rateLimitEnabled) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = getHttpServletRequest(joinPoint);
        if (request == null) {
            log.warn("无法获取请求对象，跳过限流检查");
            return joinPoint.proceed();
        }

        String clientId = getClientId(request);
        String key = cachePrefix + clientId;

        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - 60 * 1000; // 窗口起始时间（1分钟前）

        // 获取当前窗口内的请求数
        Long count = redisTemplate.opsForZSet().count(key, windowStart, currentTime);

        if (count != null && count >= requestsPerMinute) {
            int retryAfter = 60;
            try {
                var range = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
                if (range != null && !range.isEmpty()) {
                    var firstEntry = range.iterator().next();
                    long oldestRequestTime = firstEntry.getScore().longValue();
                    long timeUntilExpiry = 60 * 1000 - (currentTime - oldestRequestTime);
                    retryAfter = Math.max(1, (int) (timeUntilExpiry / 1000));
                }
            } catch (Exception e) {
                log.warn("获取最早请求时间失败: {}", e.getMessage());
            }

            log.warn("用户 {} 请求过于频繁，已触发限流", clientId);
            ContentOptimizationResponse response = ContentOptimizationResponse.rateLimitExceeded(retryAfter);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        // 记录当前请求时间
        redisTemplate.opsForZSet().add(key, String.valueOf(currentTime), currentTime);

        // 清理过期数据（保留1分钟的数据）
        redisTemplate.opsForZSet().removeRange(key, 0, -1);
        redisTemplate.opsForZSet().add(key, String.valueOf(currentTime), currentTime);

        // 设置过期时间
        redisTemplate.expire(key, 60, TimeUnit.SECONDS);

        return joinPoint.proceed();
    }

    /**
     * 从切点获取 HttpServletRequest
     */
    private HttpServletRequest getHttpServletRequest(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest) {
                return (HttpServletRequest) arg;
            }
        }
        return null;
    }

    /**
     * 获取客户端标识（优先使用用户ID，否则使用IP）
     */
    private String getClientId(HttpServletRequest request) {
        // 尝试从请求体中获取 userId
        try {
            request.getInputStream().reset();
            byte[] body = request.getInputStream().readAllBytes();
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            if (!bodyStr.isEmpty()) {
                try {
                    var node = objectMapper.readTree(bodyStr);
                    if (node.has("userId") && !node.get("userId").isNull()) {
                        return node.get("userId").asText();
                    }
                } catch (Exception e) {
                    // 忽略JSON解析错误
                }
            }
        } catch (Exception e) {
            // 忽略
        }

        // 使用客户端IP作为标识
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getHeader("X-Real-IP");
        }
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }

        // 如果是多个IP（通过代理），取第一个
        if (clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }

        return clientIp;
    }
}