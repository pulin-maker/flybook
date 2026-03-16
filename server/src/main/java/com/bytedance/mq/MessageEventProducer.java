package com.bytedance.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.event.MessageSentEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 消息事件生产者
 * 在事务提交后将消息事件发送到 RocketMQ，确保不会为回滚的事务发送事件
 */
@Component
@Slf4j
public class MessageEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${flybook.mq.topic:flybook-message-push}")
    private String topic;

    public MessageEventProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 注册事务提交后的 MQ 发送回调
     * 确保 MySQL 事务提交成功后才发送 MQ 消息
     */
    public void sendAfterCommit(MessageSentEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(event);
                }
            });
        } else {
            // 非事务上下文，直接发送
            doSend(event);
        }
    }

    private void doSend(MessageSentEvent event) {
        try {
            String json = JSONUtil.toJsonStr(event);
            rocketMQTemplate.convertAndSend(topic, json);
            log.debug("MQ 消息发送成功: messageId={}, conversationId={}", event.getMessageId(), event.getConversationId());
        } catch (Exception e) {
            // MQ 发送失败不影响主流程，消息已在 MySQL 中，客户端可通过 sync API 拉取
            log.error("MQ 消息发送失败: messageId={}", event.getMessageId(), e);
        }
    }
}
