package com.bytedance.infrastructure.websocket;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ACK 待确认服务
 * 使用 Redis 管理待 ACK 的消息状态和重试队列
 *
 * Redis 结构:
 * 1. String: ack:pending:{messageId}:{userId} -> {"retryCount":0,"pushJson":"..."}, TTL 30s
 * 2. Sorted Set: ack:retry-queue -> member={messageId}:{userId}, score=下次重试时间戳
 */
@Component
@Slf4j
public class AckPendingService {

    private static final String PENDING_KEY_PREFIX = "ack:pending:";
    private static final String RETRY_QUEUE_KEY = "ack:retry-queue";
    private static final long PENDING_TTL_SECONDS = 30;
    private static final long RETRY_INTERVAL_MS = 10_000;
    private static final int MAX_RETRY_COUNT = 3;

    private final StringRedisTemplate redisTemplate;

    public AckPendingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 注册一条待 ACK 的推送消息
     */
    public void registerPendingAck(Long messageId, Long userId, String pushJson) {
        try {
            String pendingKey = buildPendingKey(messageId, userId);
            String member = buildMember(messageId, userId);

            // 写入 pending 详情（含 pushJson 和 retryCount）
            String value = JSONUtil.createObj()
                    .set("retryCount", 0)
                    .set("pushJson", pushJson)
                    .toString();
            redisTemplate.opsForValue().set(pendingKey, value, PENDING_TTL_SECONDS, TimeUnit.SECONDS);

            // 加入重试队列，score = 当前时间 + 重试间隔
            double nextRetryScore = System.currentTimeMillis() + RETRY_INTERVAL_MS;
            redisTemplate.opsForZSet().add(RETRY_QUEUE_KEY, member, nextRetryScore);

            log.debug("注册 pending ACK: messageId={}, userId={}", messageId, userId);
        } catch (Exception e) {
            log.warn("注册 pending ACK 失败: messageId={}, userId={}", messageId, userId, e);
        }
    }

    /**
     * 客户端确认消息已收到
     * @return true 表示有效的 ACK，false 表示重复/过期的 ACK
     */
    public boolean acknowledgeMessage(Long messageId, Long userId) {
        try {
            String pendingKey = buildPendingKey(messageId, userId);
            String member = buildMember(messageId, userId);

            Boolean deleted = redisTemplate.delete(pendingKey);
            redisTemplate.opsForZSet().remove(RETRY_QUEUE_KEY, member);

            boolean valid = Boolean.TRUE.equals(deleted);
            if (valid) {
                log.debug("ACK 确认成功: messageId={}, userId={}", messageId, userId);
            }
            return valid;
        } catch (Exception e) {
            log.warn("ACK 处理失败: messageId={}, userId={}", messageId, userId, e);
            return false;
        }
    }

    /**
     * 获取已到重试时间的待确认消息
     */
    public List<PendingAckEntry> getExpiredPendingAcks() {
        try {
            double now = System.currentTimeMillis();
            Set<ZSetOperations.TypedTuple<String>> entries =
                    redisTemplate.opsForZSet().rangeByScoreWithScores(RETRY_QUEUE_KEY, 0, now);

            if (entries == null || entries.isEmpty()) {
                return Collections.emptyList();
            }

            List<PendingAckEntry> result = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> entry : entries) {
                String member = entry.getValue();
                if (member == null) continue;

                String[] parts = member.split(":");
                if (parts.length != 2) continue;

                Long messageId = Long.valueOf(parts[0]);
                Long userId = Long.valueOf(parts[1]);
                String pendingKey = buildPendingKey(messageId, userId);

                // 读取 pending 详情
                String value = redisTemplate.opsForValue().get(pendingKey);
                if (value == null) {
                    // pending key 已过期（TTL），清除 sorted set 中的残留
                    redisTemplate.opsForZSet().remove(RETRY_QUEUE_KEY, member);
                    continue;
                }

                int retryCount = JSONUtil.parseObj(value).getInt("retryCount", 0);
                String pushJson = JSONUtil.parseObj(value).getStr("pushJson");

                result.add(PendingAckEntry.builder()
                        .messageId(messageId)
                        .userId(userId)
                        .retryCount(retryCount)
                        .pushJson(pushJson)
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("获取过期 pending ACK 失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 增加重试次数，更新下次重试时间
     */
    public void incrementRetryCount(Long messageId, Long userId) {
        try {
            String pendingKey = buildPendingKey(messageId, userId);
            String member = buildMember(messageId, userId);

            String value = redisTemplate.opsForValue().get(pendingKey);
            if (value == null) return;

            int retryCount = JSONUtil.parseObj(value).getInt("retryCount", 0) + 1;
            String pushJson = JSONUtil.parseObj(value).getStr("pushJson");

            // 更新 retryCount
            String newValue = JSONUtil.createObj()
                    .set("retryCount", retryCount)
                    .set("pushJson", pushJson)
                    .toString();
            redisTemplate.opsForValue().set(pendingKey, newValue, PENDING_TTL_SECONDS, TimeUnit.SECONDS);

            // 更新下次重试时间
            double nextRetryScore = System.currentTimeMillis() + RETRY_INTERVAL_MS;
            redisTemplate.opsForZSet().add(RETRY_QUEUE_KEY, member, nextRetryScore);
        } catch (Exception e) {
            log.warn("incrementRetryCount 失败: messageId={}, userId={}", messageId, userId, e);
        }
    }

    /**
     * 放弃重试（超过最大重试次数或用户离线）
     */
    public void giveUp(Long messageId, Long userId) {
        try {
            String pendingKey = buildPendingKey(messageId, userId);
            String member = buildMember(messageId, userId);

            redisTemplate.delete(pendingKey);
            redisTemplate.opsForZSet().remove(RETRY_QUEUE_KEY, member);

            log.debug("放弃 ACK 重试: messageId={}, userId={}", messageId, userId);
        } catch (Exception e) {
            log.warn("giveUp 失败: messageId={}, userId={}", messageId, userId, e);
        }
    }

    public int getMaxRetryCount() {
        return MAX_RETRY_COUNT;
    }

    private String buildPendingKey(Long messageId, Long userId) {
        return PENDING_KEY_PREFIX + messageId + ":" + userId;
    }

    private String buildMember(Long messageId, Long userId) {
        return messageId + ":" + userId;
    }
}
