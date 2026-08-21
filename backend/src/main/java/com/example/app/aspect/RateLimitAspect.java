package com.example.app.aspect;

import com.example.app.dto.ContentOptimizationResponse;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 使用 Redis ZSet 实现滑动窗口限流。
 *
 * <p>客户端标识策略：优先从请求头 {@code X-User-Id} 读取（鉴权后由网关注入），
 * 否则回退到客户端 IP。不再读取请求体，因为 {@code @RequestBody} 消费后
 * ServletInputStream 无法 reset()，会导致请求解析失败。
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${rate-limit.requests-per-minute:10}")
    private int requestsPerMinute;

    @Value("${rate-limit.cache-prefix:optimize:rate:}")
    private String cachePrefix;

    @Value("${rate-limit.window-seconds:60}")
    private int windowSeconds;

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

        HttpServletRequest request = resolveRequest(joinPoint);
        if (request == null) {
            log.warn("无法获取请求对象，跳过限流检查");
            return joinPoint.proceed();
        }

        String clientId = getClientId(request);
        String key = cachePrefix + clientId;

        long currentTime = System.currentTimeMillis();
        long windowMillis = (long) windowSeconds * 1000L;
        long windowStart = currentTime - windowMillis;

        // 获取当前窗口内的请求数
        Long count = redisTemplate.opsForZSet().count(key, windowStart, currentTime);

        if (count != null && count >= requestsPerMinute) {
            int retryAfter = windowSeconds;
            try {
                var range = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
                if (range != null && !range.isEmpty()) {
                    var firstEntry = range.iterator().next();
                    long oldestRequestTime = firstEntry.getScore().longValue();
                    long timeUntilExpiry = windowMillis - (currentTime - oldestRequestTime);
                    retryAfter = Math.max(1, (int) (timeUntilExpiry / 1000));
                }
            } catch (Exception e) {
                log.warn("获取最早请求时间失败: {}", e.getMessage());
            }

            log.warn("用户 {} 请求过于频繁，已触发限流", clientId);
            ContentOptimizationResponse response = ContentOptimizationResponse.rateLimitExceeded(retryAfter);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        // 记录当前请求时间（ZSet 按 member 去重，同一 ms 内重复 add 是 no-op，
        // 不再重复调用以避免无意义的 Redis 往返）
        redisTemplate.opsForZSet().add(key, String.valueOf(currentTime), currentTime);

        // 清理过期数据
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 设置过期时间
        redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

        return joinPoint.proceed();
    }

    /**
     * 解析当前 HttpServletRequest。
     * 优先从切点方法参数中查找；若未找到（方法签名无 HttpServletRequest 参数），
     * 回退到 Spring {@link RequestContextHolder} 获取当前请求。
     */
    private HttpServletRequest resolveRequest(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof HttpServletRequest httpReq) {
                    return httpReq;
                }
            }
        }
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 获取客户端标识。
     * 优先使用请求头 {@code X-User-Id}（鉴权后由网关注入），
     * 否则回退到客户端 IP。不再读取请求体，避免破坏 {@code @RequestBody} 解析。
     */
    private String getClientId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
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
        if (clientIp != null && clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }

        return "ip:" + clientIp;
    }
}