package com.bytedance.usecase.message;

import cn.hutool.json.JSONUtil;
import com.bytedance.entity.Message;
import com.bytedance.entity.MqOutbox;
import com.bytedance.event.MessageEditedEvent;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import com.bytedance.mapper.MqOutboxMapper;
import com.bytedance.mq.MessageEventProducer;
import com.bytedance.mq.RoutingKeys;
import com.bytedance.repository.IMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 消息编辑用例
 * 仅发送者本人可编辑，24 小时时间窗口，已撤回消息不可编辑
 */
@Component
public class EditMessageUseCase {

    private final IMessageRepository messageRepository;
    private final MqOutboxMapper mqOutboxMapper;
    private final MessageEventProducer messageEventProducer;

    @Value("${flybook.mq.exchange:flybook.message}")
    private String mqExchange;

    @Autowired
    public EditMessageUseCase(IMessageRepository messageRepository,
                              MqOutboxMapper mqOutboxMapper,
                              MessageEventProducer messageEventProducer) {
        this.messageRepository = messageRepository;
        this.mqOutboxMapper = mqOutboxMapper;
        this.messageEventProducer = messageEventProducer;
    }

    @Transactional(rollbackFor = Exception.class)
    public Message execute(Long messageId, Long operatorId, String newContentJson) {
        // 1. 查找消息
        Message message = messageRepository.findById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        // 2. 权限：仅发送者可编辑
        if (!message.getSenderId().equals(operatorId)) {
            throw new BizException(ErrorCode.MESSAGE_EDIT_NO_PERMISSION);
        }

        // 3. 已撤回消息不可编辑
        if (message.getIsRevoked() != null && message.getIsRevoked() == 1) {
            throw new BizException(ErrorCode.MESSAGE_REVOKED);
        }

        // 4. 时间窗口：24 小时
        long hours = Duration.between(message.getCreatedTime(), LocalDateTime.now()).toHours();
        if (hours > 24) {
            throw new BizException(ErrorCode.MESSAGE_EDIT_TIMEOUT);
        }

        // 5. 更新消息
        LocalDateTime now = LocalDateTime.now();
        message.setIsEdited(1);
        message.setEditedContent(newContentJson);
        message.setEditTime(now);
        messageRepository.update(message);

        // 6. 发送编辑事件
        MessageEditedEvent event = MessageEditedEvent.builder()
                .messageId(messageId)
                .conversationId(message.getConversationId())
                .senderId(operatorId)
                .newContent(newContentJson)
                .editTime(now.toString())
                .build();

        MqOutbox outbox = MqOutbox.builder()
                .messageId(messageId)
                .exchange(mqExchange)
                .routingKey(RoutingKeys.MSG_EDITED)
                .body(JSONUtil.toJsonStr(event))
                .status(MqOutbox.Status.PENDING)
                .retryCount(0)
                .build();
        mqOutboxMapper.insert(outbox);

        messageEventProducer.sendEventAfterCommit(event, RoutingKeys.MSG_EDITED);
        return message;
    }
}
