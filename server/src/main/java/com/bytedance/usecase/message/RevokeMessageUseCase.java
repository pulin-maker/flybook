package com.bytedance.usecase.message;

import cn.hutool.json.JSONUtil;
import com.bytedance.entity.ConversationMember;
import com.bytedance.entity.Message;
import com.bytedance.entity.MqOutbox;
import com.bytedance.event.MessageRevokedEvent;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import com.bytedance.mapper.MqOutboxMapper;
import com.bytedance.mq.MessageEventProducer;
import com.bytedance.mq.RoutingKeys;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.repository.IMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 消息撤回用例
 * 仅发送者和群管理员/群主可撤回，2 分钟时间窗口
 */
@Component
public class RevokeMessageUseCase {

    private final IMessageRepository messageRepository;
    private final IConversationMemberRepository conversationMemberRepository;
    private final MqOutboxMapper mqOutboxMapper;
    private final MessageEventProducer messageEventProducer;

    @Value("${flybook.mq.exchange:flybook.message}")
    private String mqExchange;

    @Autowired
    public RevokeMessageUseCase(IMessageRepository messageRepository,
                                IConversationMemberRepository conversationMemberRepository,
                                MqOutboxMapper mqOutboxMapper,
                                MessageEventProducer messageEventProducer) {
        this.messageRepository = messageRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.mqOutboxMapper = mqOutboxMapper;
        this.messageEventProducer = messageEventProducer;
    }

    @Transactional(rollbackFor = Exception.class)
    public void execute(Long messageId, Long operatorId) {
        // 1. 查找消息
        Message message = messageRepository.findById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        // 2. 幂等检查
        if (message.getIsRevoked() != null && message.getIsRevoked() == 1) {
            return;
        }

        // 3. 权限检查：发送者本人 or 群管理员/群主
        boolean isSender = message.getSenderId().equals(operatorId);
        if (!isSender) {
            ConversationMember member = conversationMemberRepository
                    .findByConversationIdAndUserId(message.getConversationId(), operatorId);
            if (member == null || member.getRole() < 1) {
                throw new BizException(ErrorCode.MESSAGE_REVOKE_NO_PERMISSION);
            }
        }

        // 4. 时间窗口：发送者 2 分钟内，管理员不限
        if (isSender) {
            long minutes = Duration.between(message.getCreatedTime(), LocalDateTime.now()).toMinutes();
            if (minutes > 2) {
                throw new BizException(ErrorCode.MESSAGE_REVOKE_TIMEOUT);
            }
        }

        // 5. 更新撤回状态（精确路由到分片）
        messageRepository.updateRevokeStatus(messageId, message.getConversationId());

        // 6. 发送撤回事件
        LocalDateTime now = LocalDateTime.now();
        MessageRevokedEvent event = MessageRevokedEvent.builder()
                .messageId(messageId)
                .conversationId(message.getConversationId())
                .operatorId(operatorId)
                .revokedTime(now.toString())
                .build();

        MqOutbox outbox = MqOutbox.builder()
                .messageId(messageId)
                .exchange(mqExchange)
                .routingKey(RoutingKeys.MSG_REVOKED)
                .body(JSONUtil.toJsonStr(event))
                .status(MqOutbox.Status.PENDING)
                .retryCount(0)
                .build();
        mqOutboxMapper.insert(outbox);

        messageEventProducer.sendEventAfterCommit(event, RoutingKeys.MSG_REVOKED);
    }
}
