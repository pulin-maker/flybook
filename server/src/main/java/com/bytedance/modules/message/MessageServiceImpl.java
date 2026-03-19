package com.bytedance.modules.message;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.modules.conversation.ConversationCacheService;
import com.bytedance.modules.conversation.Conversation;
import com.bytedance.modules.conversation.ConversationMember;
import com.bytedance.modules.conversation.ConversationMapper;
import com.bytedance.modules.conversation.ConversationMemberMapper;
import com.bytedance.infrastructure.mq.MqOutbox;
import com.bytedance.infrastructure.mq.MqOutboxMapper;
import com.bytedance.modules.message.event.MessageEditedEvent;
import com.bytedance.modules.message.event.MessageRevokedEvent;
import com.bytedance.modules.message.event.MessageSentEvent;
import com.bytedance.modules.message.event.ReactionChangedEvent;
import com.bytedance.common.exception.BizException;
import com.bytedance.common.exception.ErrorCode;
import com.bytedance.modules.message.mq.MessageEventProducer;
import com.bytedance.infrastructure.mq.RoutingKeys;
import com.bytedance.modules.message.IMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements IMessageService {

    private final MessageMapper messageMapper;
    private final MessageReactionMapper messageReactionMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final Snowflake snowflake;
    private final MessageEventProducer messageEventProducer;
    private final MqOutboxMapper mqOutboxMapper;
    private final ConversationCacheService conversationCacheService;

    @Value("${flybook.mq.exchange:flybook.message}")
    private String mqExchange;

    @Autowired
    public MessageServiceImpl(MessageMapper messageMapper,
                             MessageReactionMapper messageReactionMapper,
                             ConversationMapper conversationMapper,
                             ConversationMemberMapper conversationMemberMapper,
                             Snowflake snowflake,
                             MessageEventProducer messageEventProducer,
                             MqOutboxMapper mqOutboxMapper,
                             ConversationCacheService conversationCacheService) {
        this.messageMapper = messageMapper;
        this.messageReactionMapper = messageReactionMapper;
        this.conversationMapper = conversationMapper;
        this.conversationMemberMapper = conversationMemberMapper;
        this.snowflake = snowflake;
        this.messageEventProducer = messageEventProducer;
        this.mqOutboxMapper = mqOutboxMapper;
        this.conversationCacheService = conversationCacheService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message sendMessage(Long conversationId, Long senderId, Integer msgType, String contentJson,
                               List<Long> mentionUserIds, Long quoteId) {
        // 1. 校验是否在会话中
        ConversationMember senderMember = conversationMemberMapper.selectOne(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, senderId));
        if (senderMember == null) {
            throw new BizException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }

        // 2. 禁言检查
        if (senderMember.getIsMuted() != null && senderMember.getIsMuted() == 1) {
            throw new BizException(ErrorCode.USER_IS_MUTED);
        }

        // 3. 校验会话存在
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        // 4. Snowflake 生成序列号
        long newSeq = snowflake.nextId();

        // 5. 序列化 mentions
        String mentionsJson = null;
        if (mentionUserIds != null && !mentionUserIds.isEmpty()) {
            mentionsJson = JSONUtil.toJsonStr(mentionUserIds);
        }

        // 6. 构建消息
        LocalDateTime now = LocalDateTime.now();
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .seq(newSeq)
                .msgType(msgType)
                .content(contentJson)
                .mentions(mentionsJson)
                .quoteId(quoteId)
                .createdTime(now)
                .build();
        messageMapper.insert(message);

        // 7. CAS 更新会话摘要
        String summary = "[未知消息]";
        if (msgType == 1) {
            summary = JSONUtil.parseObj(contentJson).getStr("text");
        } else if (msgType == 2) {
            summary = "[图片]";
        } else if (msgType == 5) {
            summary = "[待办任务]";
        }
        conversationMapper.casUpdateSeqAndSummary(conversationId, newSeq, summary, now);

        // 淘汰会话缓存
        conversationCacheService.evict(conversationId);

        // 8. 更新未读数
        conversationMemberMapper.incrementUnreadCount(conversationId, senderId);

        // 9. 构建事件
        MessageSentEvent event = MessageSentEvent.builder()
                .messageId(message.getMessageId())
                .conversationId(conversationId)
                .senderId(senderId)
                .seq(newSeq)
                .msgType(msgType)
                .content(contentJson)
                .createdTime(now.toString())
                .mentions(mentionsJson)
                .quoteId(quoteId)
                .build();

        // 10. Outbox
        MqOutbox outbox = MqOutbox.builder()
                .messageId(message.getMessageId())
                .exchange(mqExchange)
                .routingKey(RoutingKeys.MSG_SENT)
                .body(JSONUtil.toJsonStr(event))
                .status(MqOutbox.Status.PENDING)
                .retryCount(0)
                .build();
        mqOutboxMapper.insert(outbox);

        // 11. 事务提交后异步推送
        messageEventProducer.sendAfterCommit(event);

        return message;
    }

    @Override
    public Message sendTextMsg(Long conversationId, Long senderId, String text) {
        String json = JSONUtil.createObj().set("text", text).toString();
        return sendMessage(conversationId, senderId, 1, json, null, null);
    }

    @Override
    public List<Message> syncMessages(Long conversationId, Long afterSeq) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .gt(Message::getSeq, afterSeq)
                        .orderByAsc(Message::getSeq)
                        .last("LIMIT 100"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeMessage(Long messageId, Long operatorId) {
        // 1. 查找消息
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        // 2. 幂等检查
        if (message.getIsRevoked() != null && message.getIsRevoked() == 1) {
            return;
        }

        // 3. 权限检查
        boolean isSender = message.getSenderId().equals(operatorId);
        if (!isSender) {
            ConversationMember member = conversationMemberMapper.selectOne(
                    new LambdaQueryWrapper<ConversationMember>()
                            .eq(ConversationMember::getConversationId, message.getConversationId())
                            .eq(ConversationMember::getUserId, operatorId));
            if (member == null || member.getRole() < 1) {
                throw new BizException(ErrorCode.MESSAGE_REVOKE_NO_PERMISSION);
            }
        }

        // 4. 时间窗口
        if (isSender) {
            long minutes = Duration.between(message.getCreatedTime(), LocalDateTime.now()).toMinutes();
            if (minutes > 2) {
                throw new BizException(ErrorCode.MESSAGE_REVOKE_TIMEOUT);
            }
        }

        // 5. 更新撤回状态
        messageMapper.update(null,
                new LambdaUpdateWrapper<Message>()
                        .eq(Message::getMessageId, messageId)
                        .eq(Message::getConversationId, message.getConversationId())
                        .set(Message::getIsRevoked, 1));

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message editMessage(Long messageId, Long operatorId, String newContentJson) {
        // 1. 查找消息
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        // 2. 仅发送者可编辑
        if (!message.getSenderId().equals(operatorId)) {
            throw new BizException(ErrorCode.MESSAGE_EDIT_NO_PERMISSION);
        }

        // 3. 已撤回消息不可编辑
        if (message.getIsRevoked() != null && message.getIsRevoked() == 1) {
            throw new BizException(ErrorCode.MESSAGE_REVOKED);
        }

        // 4. 24 小时时间窗口
        long hours = Duration.between(message.getCreatedTime(), LocalDateTime.now()).toHours();
        if (hours > 24) {
            throw new BizException(ErrorCode.MESSAGE_EDIT_TIMEOUT);
        }

        // 5. 更新消息
        LocalDateTime now = LocalDateTime.now();
        message.setIsEdited(1);
        message.setEditedContent(newContentJson);
        message.setEditTime(now);
        messageMapper.update(message,
                new LambdaUpdateWrapper<Message>()
                        .eq(Message::getMessageId, message.getMessageId())
                        .eq(Message::getConversationId, message.getConversationId()));

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

    @Override
    public boolean toggleReaction(Long messageId, Long userId, String reactionType) {
        // 1. 校验消息存在
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        // 2. 切换
        Long conversationId = message.getConversationId();
        boolean exists = messageReactionMapper.selectCount(
                new LambdaQueryWrapper<MessageReaction>()
                        .eq(MessageReaction::getConversationId, conversationId)
                        .eq(MessageReaction::getMessageId, messageId)
                        .eq(MessageReaction::getUserId, userId)
                        .eq(MessageReaction::getReactionType, reactionType)) > 0;

        boolean added;
        if (exists) {
            messageReactionMapper.delete(
                    new LambdaQueryWrapper<MessageReaction>()
                            .eq(MessageReaction::getConversationId, conversationId)
                            .eq(MessageReaction::getMessageId, messageId)
                            .eq(MessageReaction::getUserId, userId)
                            .eq(MessageReaction::getReactionType, reactionType));
            added = false;
        } else {
            MessageReaction reaction = MessageReaction.builder()
                    .conversationId(conversationId)
                    .messageId(messageId)
                    .userId(userId)
                    .reactionType(reactionType)
                    .createdTime(LocalDateTime.now())
                    .build();
            messageReactionMapper.insert(reaction);
            added = true;
        }

        // 3. 发送 MQ 事件
        ReactionChangedEvent event = ReactionChangedEvent.builder()
                .messageId(messageId)
                .conversationId(conversationId)
                .userId(userId)
                .reactionType(reactionType)
                .added(added)
                .build();
        messageEventProducer.sendEventAfterCommit(event, RoutingKeys.REACTION_CHANGED);

        return added;
    }

    @Override
    public List<MessageReaction> getReactions(Long conversationId, Long messageId) {
        return messageReactionMapper.selectList(
                new LambdaQueryWrapper<MessageReaction>()
                        .eq(MessageReaction::getConversationId, conversationId)
                        .eq(MessageReaction::getMessageId, messageId)
                        .orderByAsc(MessageReaction::getCreatedTime));
    }
}
