package com.bytedance.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.websocket.Session;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 分布式会话注册表
 * 本地 ConcurrentHashMap 存储实际 Session 对象
 * Redis 存储 userId -> serverId 映射，支持多实例水平扩展
 */
@Component
@Slf4j
public class SessionRegistry {

    private static final String SESSION_KEY_PREFIX = "ws:session:";
    private static final long SESSION_TTL_SECONDS = 120;

    private final ConcurrentHashMap<Long, Session> localSessions = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final String serverId;

    public SessionRegistry(StringRedisTemplate redisTemplate,
                           @Qualifier("serverId") String serverId) {
        this.redisTemplate = redisTemplate;
        this.serverId = serverId;
    }

    /**
     * 注册会话：写入本地 Map + Redis
     */
    public void register(Long userId, Session session) {
        localSessions.put(userId, session);
        try {
            redisTemplate.opsForValue().set(
                    SESSION_KEY_PREFIX + userId, serverId, SESSION_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 会话注册失败，仅使用本地会话: userId={}", userId, e);
        }
        log.info("用户上线: userId={}, serverId={}, 本地在线: {}", userId, serverId, localSessions.size());
    }

    /**
     * 注销会话：删除本地 Map + 条件删除 Redis（仅当 value 匹配本机 serverId）
     */
    public void unregister(Long userId) {
        localSessions.remove(userId);
        try {
            String key = SESSION_KEY_PREFIX + userId;
            String value = redisTemplate.opsForValue().get(key);
            if (serverId.equals(value)) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.warn("Redis 会话注销失败: userId={}", userId, e);
        }
        log.info("用户下线: userId={}, 本地在线: {}", userId, localSessions.size());
    }

    /**
     * 心跳续期：刷新 Redis TTL
     */
    public void refreshTtl(Long userId) {
        try {
            redisTemplate.expire(SESSION_KEY_PREFIX + userId, SESSION_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis TTL 续期失败: userId={}", userId);
        }
    }

    /**
     * 推送消息到本地会话
     * @return true 推送成功，false 用户不在本机
     */
    public boolean pushMessage(Long userId, String message) {
        Session session = localSessions.get(userId);
        if (session != null && session.isOpen()) {
            synchronized (session) {
                try {
                    session.getBasicRemote().sendText(message);
                    return true;
                } catch (IOException e) {
                    log.error("消息推送失败: userId={}", userId, e);
                    try {
                        session.close();
                    } catch (IOException ex) {
                        // ignore
                    }
                }
            }
        }
        return false;
    }

    /**
     * 判断用户是否在线（任意实例）
     */
    public boolean isOnlineAnywhere(Long userId) {
        if (localSessions.containsKey(userId)) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(SESSION_KEY_PREFIX + userId));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取用户所在的远程服务器ID（用于跨实例路由）
     */
    public String getRemoteServerId(Long userId) {
        try {
            return redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + userId);
        } catch (Exception e) {
            return null;
        }
    }

    public int getLocalOnlineCount() {
        return localSessions.size();
    }
}
