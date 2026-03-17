package com.bytedance.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.entity.ConversationMember;
import com.bytedance.event.MessageSentEvent;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.resilience.RedisHealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 消息推送消费者
 * 从 RabbitMQ 消费消息事件，通过 WebSocket 推送给在线用户
 * 推送成功后注册 pending ACK，等待客户端确认
 * 支持 Redis 幂等去重 + 死信队列 + RoutingKey 路由
 */
@Component
@Slf4j
public class MessagePushConsumer {

    private static final String CONSUMED_KEY_PREFIX = "mq:consumed:";
    private static final long CONSUMED_TTL_HOURS = 24;

    private static final int PARALLEL_THRESHOLD = 20; // 超过此成员数启用并行推送

    private final IConversationMemberRepository conversationMemberRepository;
    private final WsBroadcastService wsBroadcastService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisHealthChecker redisHealthChecker;
    private final Executor pushExecutor;

    public MessagePushConsumer(IConversationMemberRepository conversationMemberRepository,
                               WsBroadcastService wsBroadcastService,
                               StringRedisTemplate stringRedisTemplate,
                               RedisHealthChecker redisHealthChecker,
                               @Qualifier("pushExecutor") Executor pushExecutor) {
        this.conversationMemberRepository = conversationMemberRepository;
        this.wsBroadcastService = wsBroadcastService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisHealthChecker = redisHealthChecker;
        this.pushExecutor = pushExecutor;
    }

    @RabbitListener(queues = QueueNames.PUSH, containerFactory = "retryContainerFactory")
    public void onMessage(String eventJson) {
        MessageSentEvent event;
        try {
            event = JSONUtil.toBean(eventJson, MessageSentEvent.class);
        } catch (Exception e) {
            log.error("MQ 消息反序列化失败: {}", eventJson, e);
            return;
        }

        // 幂等检查：Redis SET NX 去重（Redis 不可用时降级放行，优先保证可用性）
        if (redisHealthChecker.isAvailable()) {
            String consumedKey = CONSUMED_KEY_PREFIX + event.getMessageId();
            Boolean isNew = stringRedisTemplate.opsForValue()
                    .setIfAbsent(consumedKey, "1", CONSUMED_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Duplicate MQ message skipped: messageId={}", event.getMessageId());
                return;
            }
        } else {
            log.warn("Redis 不可用，跳过幂等检查: messageId={}", event.getMessageId());
        }

        log.debug("MQ 消费消息: messageId={}, conversationId={}", event.getMessageId(), event.getConversationId());

        // 1. 查询会话所有成员
        List<ConversationMember> members = conversationMemberRepository
                .findByConversationId(event.getConversationId());

        // 2. 构建推送 JSON（与原 Message 实体格式一致）
        String pushJson = buildPushJson(event);

        // 3. 推送给每个在线成员（排除发送者）
        //    大群（>20人）启用并行推送
        List<ConversationMember> targets = members.stream()
                .filter(m -> !m.getUserId().equals(event.getSenderId()))
                .collect(java.util.stream.Collectors.toList());

        if (targets.size() > PARALLEL_THRESHOLD) {
            // 并行推送
            CompletableFuture<?>[] futures = targets.stream()
                    .map(member -> CompletableFuture.runAsync(() ->
                            pushToMember(member, event, pushJson), pushExecutor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
        } else {
            // 串行推送
            for (ConversationMember member : targets) {
                pushToMember(member, event, pushJson);
            }
        }
    }

    private void pushToMember(ConversationMember member, MessageSentEvent event, String pushJson) {
        wsBroadcastService.broadcast(member.getUserId(), pushJson, event.getMessageId(), true);
        log.debug("广播推送: userId={}, messageId={}", member.getUserId(), event.getMessageId());
    }

    /**
     * 构建与 Message 实体 JSON 格式一致的推送内容
     */
    private String buildPushJson(MessageSentEvent event) {
        return JSONUtil.createObj()
                .set("messageId", event.getMessageId())
                .set("conversationId", event.getConversationId())
                .set("senderId", event.getSenderId())
                .set("seq", event.getSeq())
                .set("msgType", event.getMsgType())
                .set("content", event.getContent())
                .set("createdTime", event.getCreatedTime())
                .set("mentions", event.getMentions())
                .set("quoteId", event.getQuoteId())
                .toString();
    }
}
