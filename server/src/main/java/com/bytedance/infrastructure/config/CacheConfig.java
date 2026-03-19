package com.bytedance.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置
 */
@Configuration
public class CacheConfig {

    /**
     * 用户信息缓存（L1）
     * 最大 1000 条，写入后 5 分钟过期
     */
    @Bean
    public Cache<String, Object> userCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 会话信息缓存（L1）
     * 最大 500 条，写入后 3 分钟过期
     */
    @Bean
    public Cache<String, Object> conversationCache() {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(3, TimeUnit.MINUTES)
                .build();
    }
}
