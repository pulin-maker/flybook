package com.bytedance.modules.conversation;
import com.bytedance.infrastructure.cache.TwoLevelCache;

import com.bytedance.modules.conversation.Conversation;
import com.bytedance.modules.conversation.ConversationMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 会话信息二级缓存
 * L1: Caffeine (3min) → L2: Redis (30min) → DB
 */
@Component
public class ConversationCacheService {

    private final TwoLevelCache<Conversation> cache;
    private final ConversationMapper conversationMapper;

    public ConversationCacheService(Cache<String, Object> conversationCache,
                                    StringRedisTemplate redisTemplate,
                                    ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
        this.cache = new TwoLevelCache<>(conversationCache, redisTemplate, "cache:conv:", 1800, Conversation.class);
    }

    public Conversation getById(Long conversationId) {
        return cache.get(String.valueOf(conversationId),
                key -> conversationMapper.selectById(Long.parseLong(key)));
    }

    public void evict(Long conversationId) {
        cache.evict(String.valueOf(conversationId));
    }
}
