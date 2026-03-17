package com.bytedance.usecase.message;

import com.bytedance.entity.Message;
import com.bytedance.entity.MessageReaction;
import com.bytedance.event.ReactionChangedEvent;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import com.bytedance.mq.MessageEventProducer;
import com.bytedance.mq.RoutingKeys;
import com.bytedance.repository.IMessageReactionRepository;
import com.bytedance.repository.IMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 切换表情回应用例
 * 已存在则删除（取消），不存在则新增（添加）
 */
@Component
public class ToggleReactionUseCase {

    private final IMessageReactionRepository reactionRepository;
    private final IMessageRepository messageRepository;
    private final MessageEventProducer messageEventProducer;

    @Autowired
    public ToggleReactionUseCase(IMessageReactionRepository reactionRepository,
                                 IMessageRepository messageRepository,
                                 MessageEventProducer messageEventProducer) {
        this.reactionRepository = reactionRepository;
        this.messageRepository = messageRepository;
        this.messageEventProducer = messageEventProducer;
    }

    /**
     * @return true=添加了表情, false=取消了表情
     */
    public boolean execute(Long messageId, Long userId, String reactionType) {
        // 1. 校验消息存在
        Message message = messageRepository.findById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        // 2. 切换
        Long conversationId = message.getConversationId();
        boolean exists = reactionRepository.exists(conversationId, messageId, userId, reactionType);
        boolean added;
        if (exists) {
            reactionRepository.delete(conversationId, messageId, userId, reactionType);
            added = false;
        } else {
            MessageReaction reaction = MessageReaction.builder()
                    .conversationId(conversationId)
                    .messageId(messageId)
                    .userId(userId)
                    .reactionType(reactionType)
                    .createdTime(LocalDateTime.now())
                    .build();
            reactionRepository.save(reaction);
            added = true;
        }

        // 3. 发送 MQ 事件通知
        ReactionChangedEvent event = ReactionChangedEvent.builder()
                .messageId(messageId)
                .conversationId(message.getConversationId())
                .userId(userId)
                .reactionType(reactionType)
                .added(added)
                .build();
        messageEventProducer.sendEventAfterCommit(event, RoutingKeys.REACTION_CHANGED);

        return added;
    }
}
