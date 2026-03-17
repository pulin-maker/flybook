package com.bytedance.cache;

import com.bytedance.entity.Conversation;
import com.bytedance.repository.IConversationRepository;
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
    private final IConversationRepository conversationRepository;

    public ConversationCacheService(Cache<String, Object> conversationCache,
                                    StringRedisTemplate redisTemplate,
                                    IConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
        this.cache = new TwoLevelCache<>(conversationCache, redisTemplate, "cache:conv:", 1800, Conversation.class);
    }

    public Conversation getById(Long conversationId) {
        return cache.get(String.valueOf(conversationId),
                key -> conversationRepository.findById(Long.parseLong(key)));
    }

    public void evict(Long conversationId) {
        cache.evict(String.valueOf(conversationId));
    }
}
