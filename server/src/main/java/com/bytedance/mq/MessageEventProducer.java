package com.bytedance.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.entity.MqOutbox;
import com.bytedance.event.MessageSentEvent;
import com.bytedance.mapper.MqOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 消息事件生产者
 * 在事务提交后将消息事件发送到 RabbitMQ，确保不会为回滚的事务发送事件
 * 支持 Outbox Pattern 和 RoutingKey 路由
 */
@Component
@Slf4j
public class MessageEventProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MqOutboxMapper mqOutboxMapper;

    @Value("${flybook.mq.exchange:flybook.message}")
    private String exchange;

    @Autowired
    public MessageEventProducer(RabbitTemplate rabbitTemplate, MqOutboxMapper mqOutboxMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.mqOutboxMapper = mqOutboxMapper;
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
                    doSend(event, null, RoutingKeys.MSG_SENT);
                }
            });
        } else {
            doSend(event, null, RoutingKeys.MSG_SENT);
        }
    }

    /**
     * 通用事件发送（事务提交后），支持任意事件对象和 RoutingKey
     */
    public void sendEventAfterCommit(Object event, String routingKey) {
        String json = JSONUtil.toJsonStr(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSendRaw(json, routingKey);
                }
            });
        } else {
            doSendRaw(json, routingKey);
        }
    }

    /**
     * 带 Outbox 的发送（用于定时任务补偿重发）
     */
    public void sendWithOutbox(Long outboxId, String routingKey, String body) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, body);

            mqOutboxMapper.markSent(outboxId);
            log.debug("MQ 消息发送成功（Outbox 补偿）: outboxId={}", outboxId);
        } catch (Exception e) {
            log.error("MQ 消息发送失败（Outbox 补偿）: outboxId={}", outboxId, e);
            mqOutboxMapper.casUpdateStatus(outboxId, MqOutbox.Status.PENDING, MqOutbox.Status.PENDING);
        }
    }

    private void doSend(MessageSentEvent event, Long outboxId, String routingKey) {
        try {
            String json = JSONUtil.toJsonStr(event);
            rabbitTemplate.convertAndSend(exchange, routingKey, json);

            if (outboxId != null) {
                mqOutboxMapper.markSent(outboxId);
            }

            log.debug("MQ 消息发送成功: messageId={}, conversationId={}, routingKey={}",
                    event.getMessageId(), event.getConversationId(), routingKey);
        } catch (Exception e) {
            log.error("MQ 消息发送失败: messageId={}, outboxId={}", event.getMessageId(), outboxId, e);
        }
    }

    private void doSendRaw(String json, String routingKey) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, json);
            log.debug("MQ 事件发送成功: routingKey={}", routingKey);
        } catch (Exception e) {
            log.error("MQ 事件发送失败: routingKey={}", routingKey, e);
        }
    }
}
