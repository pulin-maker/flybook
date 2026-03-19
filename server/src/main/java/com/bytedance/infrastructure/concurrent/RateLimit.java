package com.bytedance.infrastructure.concurrent;

import java.lang.annotation.*;

/**
 * API 限流注解
 * 基于 Redis 滑动窗口实现
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /**
     * 时间窗口内允许的最大请求数
     */
    int permits() default 30;

    /**
     * 时间窗口大小（秒）
     */
    int window() default 60;

    /**
     * 限流 key 前缀（默认使用方法名）
     */
    String key() default "";
}
