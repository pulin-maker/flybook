package com.bytedance.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.entity.MqFailedMessage;
import com.bytedance.event.MessageSentEvent;
import com.bytedance.mapper.MqFailedMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者
 * 监听 DLQ 队列，将消费失败的消息持久化到 mq_failed_messages 表
 * 供人工排查和处理
 */
@Component
@Slf4j
public class DeadLetterConsumer {

    private final MqFailedMessageMapper mqFailedMessageMapper;

    public DeadLetterConsumer(MqFailedMessageMapper mqFailedMessageMapper) {
        this.mqFailedMessageMapper = mqFailedMessageMapper;
    }

    @RabbitListener(queues = QueueNames.DLQ)
    public void onMessage(String eventJson) {
        log.warn("收到死信消息: {}", eventJson);

        Long messageId = null;
        try {
            MessageSentEvent event = JSONUtil.toBean(eventJson, MessageSentEvent.class);
            messageId = event.getMessageId();
        } catch (Exception e) {
            log.error("死信消息反序列化失败，无法提取 messageId", e);
        }

        // 持久化到失败消息表
        MqFailedMessage failedMessage = MqFailedMessage.builder()
                .messageId(messageId)
                .exchange("flybook.message")
                .body(eventJson)
                .failReason("消费失败超过最大重试次数")
                .resolved(0)
                .build();

        mqFailedMessageMapper.insert(failedMessage);
        log.info("死信消息已持久化: failedMessageId={}, messageId={}", failedMessage.getId(), messageId);
    }
}
