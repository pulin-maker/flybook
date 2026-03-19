package com.bytedance.infrastructure.concurrent;

import com.bytedance.common.exception.BizException;
import com.bytedance.common.exception.ErrorCode;
import com.bytedance.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流 AOP 切面
 * 基于 Redis INCR + EXPIRE 实现固定窗口计数器
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 构建限流 key：rate_limit:{method}:{userId}
        String key = buildKey(joinPoint, rateLimit);

        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 第一次请求，设置过期时间
            stringRedisTemplate.expire(key, rateLimit.window(), TimeUnit.SECONDS);
        }

        if (count != null && count > rateLimit.permits()) {
            log.warn("限流触发: key={}, count={}, limit={}", key, count, rateLimit.permits());
            throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String prefix = rateLimit.key();
        if (prefix.isEmpty()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            prefix = method.getDeclaringClass().getSimpleName() + ":" + method.getName();
        }

        // 每个用户独立计数
        Long userId = UserContext.getUserId();
        String userKey = userId != null ? String.valueOf(userId) : "anonymous";

        return "rate_limit:" + prefix + ":" + userKey;
    }
}
