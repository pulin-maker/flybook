package com.bytedance.infrastructure.concurrent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 健康状态检查器
 * 定时 ping Redis，维护可用状态
 * 其他组件可通过 isAvailable() 判断是否降级
 */
@Component
@Slf4j
public class RedisHealthChecker {

    private final RedisConnectionFactory connectionFactory;
    private final AtomicBoolean available = new AtomicBoolean(true);

    public RedisHealthChecker(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * 每 10 秒检测一次 Redis 连通性
     */
    @Scheduled(fixedDelay = 10000)
    public void check() {
        try {
            connectionFactory.getConnection().ping();
            if (!available.getAndSet(true)) {
                log.info("Redis 恢复可用");
            }
        } catch (Exception e) {
            if (available.getAndSet(false)) {
                log.error("Redis 不可用，进入降级模式", e);
            }
        }
    }

    public boolean isAvailable() {
        return available.get();
    }
}
