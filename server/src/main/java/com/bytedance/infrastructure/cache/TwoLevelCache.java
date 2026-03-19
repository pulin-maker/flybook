package com.bytedance.infrastructure.cache;

import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 二级缓存：Caffeine (L1) → Redis (L2) → DB
 * L1 命中率高、延迟低；L2 跨进程共享、容量大
 *
 * @param <V> 缓存值类型
 */
@Slf4j
public class TwoLevelCache<V> {

    private final Cache<String, Object> caffeineCache;
    private final StringRedisTemplate redisTemplate;
    private final String redisKeyPrefix;
    private final long redisTtlSeconds;
    private final Class<V> valueType;

    public TwoLevelCache(Cache<String, Object> caffeineCache,
                         StringRedisTemplate redisTemplate,
                         String redisKeyPrefix,
                         long redisTtlSeconds,
                         Class<V> valueType) {
        this.caffeineCache = caffeineCache;
        this.redisTemplate = redisTemplate;
        this.redisKeyPrefix = redisKeyPrefix;
        this.redisTtlSeconds = redisTtlSeconds;
        this.valueType = valueType;
    }

    /**
     * 查询：L1 → L2 → DB loader
     */
    @SuppressWarnings("unchecked")
    public V get(String key, Function<String, V> dbLoader) {
        // 1. 查 Caffeine (L1)
        Object cached = caffeineCache.getIfPresent(key);
        if (cached != null) {
            log.debug("L1 缓存命中: key={}", key);
            return (V) cached;
        }

        // 2. 查 Redis (L2)
        try {
            String redisKey = redisKeyPrefix + key;
            String json = redisTemplate.opsForValue().get(redisKey);
            if (json != null) {
                V value = JSONUtil.toBean(json, valueType);
                caffeineCache.put(key, value); // 回填 L1
                log.debug("L2 缓存命中: key={}", key);
                return value;
            }
        } catch (Exception e) {
            log.warn("Redis L2 缓存查询失败，降级查 DB: key={}", key, e);
        }

        // 3. 查 DB
        V value = dbLoader.apply(key);
        if (value != null) {
            put(key, value);
        }
        return value;
    }

    /**
     * 写入缓存（L1 + L2）
     */
    public void put(String key, V value) {
        caffeineCache.put(key, value);
        try {
            String redisKey = redisKeyPrefix + key;
            redisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(value), redisTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis L2 缓存写入失败: key={}", key, e);
        }
    }

    /**
     * 淘汰缓存（L1 + L2）
     */
    public void evict(String key) {
        caffeineCache.invalidate(key);
        try {
            redisTemplate.delete(redisKeyPrefix + key);
        } catch (Exception e) {
            log.warn("Redis L2 缓存淘汰失败: key={}", key, e);
        }
    }
}
