package com.bytedance.infrastructure.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.modules.message.event.MessageSentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 未读计数消费者（骨架）
 * 独立队列，负责维护未读计数相关逻辑
 * 可独立扩展，不影响 WebSocket 推送消费者
 */
@Component
@Slf4j
public class UnreadCountConsumer {

    private static final String CONSUMED_KEY_PREFIX = "mq:unread-consumed:";

    private final StringRedisTemplate stringRedisTemplate;

    public UnreadCountConsumer(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @RabbitListener(queues = QueueNames.UNREAD, containerFactory = "retryContainerFactory")
    public void onMessage(String eventJson) {
        MessageSentEvent event;
        try {
            event = JSONUtil.toBean(eventJson, MessageSentEvent.class);
        } catch (Exception e) {
            log.error("UnreadCountConsumer 消息反序列化失败: {}", eventJson, e);
            return;
        }

        // 幂等检查
        String consumedKey = CONSUMED_KEY_PREFIX + event.getMessageId();
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(consumedKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            log.debug("UnreadCountConsumer duplicate skipped: messageId={}", event.getMessageId());
            return;
        }

        // TODO: 实现未读计数逻辑（如 Redis INCR、推送未读数变更等）
        log.debug("UnreadCountConsumer 处理消息: messageId={}, conversationId={}",
                event.getMessageId(), event.getConversationId());
    }
}
