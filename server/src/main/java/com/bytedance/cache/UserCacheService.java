package com.bytedance.cache;

import com.bytedance.entity.User;
import com.bytedance.repository.IUserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户信息二级缓存
 * L1: Caffeine (5min) → L2: Redis (1h) → DB
 */
@Component
public class UserCacheService {

    private final TwoLevelCache<User> cache;
    private final IUserRepository userRepository;

    public UserCacheService(Cache<String, Object> userCache,
                            StringRedisTemplate redisTemplate,
                            IUserRepository userRepository) {
        this.userRepository = userRepository;
        this.cache = new TwoLevelCache<>(userCache, redisTemplate, "cache:user:", 3600, User.class);
    }

    public User getById(Long userId) {
        return cache.get(String.valueOf(userId), key -> userRepository.findById(Long.parseLong(key)));
    }

    public void evict(Long userId) {
        cache.evict(String.valueOf(userId));
    }
}
