package com.bytedance.modules.message.mq;
import com.bytedance.infrastructure.mq.QueueNames;
import com.bytedance.infrastructure.mq.WsBroadcastService;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.modules.conversation.ConversationMember;
import com.bytedance.modules.message.event.MessageSentEvent;
import com.bytedance.modules.conversation.ConversationMemberMapper;
import com.bytedance.infrastructure.concurrent.RedisHealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MessagePushConsumer {

    private static final String CONSUMED_KEY_PREFIX = "mq:consumed:";
    private static final long CONSUMED_TTL_HOURS = 24;
    private static final int PARALLEL_THRESHOLD = 20;

    private final ConversationMemberMapper conversationMemberMapper;
    private final WsBroadcastService wsBroadcastService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisHealthChecker redisHealthChecker;
    private final Executor pushExecutor;

    public MessagePushConsumer(ConversationMemberMapper conversationMemberMapper,
                               WsBroadcastService wsBroadcastService,
                               StringRedisTemplate stringRedisTemplate,
                               RedisHealthChecker redisHealthChecker,
                               @Qualifier("pushExecutor") Executor pushExecutor) {
        this.conversationMemberMapper = conversationMemberMapper;
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

        // 幂等检查
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

        List<ConversationMember> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, event.getConversationId()));

        String pushJson = buildPushJson(event);

        List<ConversationMember> targets = members.stream()
                .filter(m -> !m.getUserId().equals(event.getSenderId()))
                .collect(java.util.stream.Collectors.toList());

        if (targets.size() > PARALLEL_THRESHOLD) {
            CompletableFuture<?>[] futures = targets.stream()
                    .map(member -> CompletableFuture.runAsync(() ->
                            pushToMember(member, event, pushJson), pushExecutor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
        } else {
            for (ConversationMember member : targets) {
                pushToMember(member, event, pushJson);
            }
        }
    }

    private void pushToMember(ConversationMember member, MessageSentEvent event, String pushJson) {
        wsBroadcastService.broadcast(member.getUserId(), pushJson, event.getMessageId(), true);
        log.debug("广播推送: userId={}, messageId={}", member.getUserId(), event.getMessageId());
    }

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
