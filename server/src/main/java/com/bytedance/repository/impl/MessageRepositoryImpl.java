package com.bytedance.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bytedance.entity.Message;
import com.bytedance.mapper.MessageMapper;
import com.bytedance.repository.IMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息数据访问实现（MySQL + ShardingSphere 分库分表）
 */
@Repository
public class MessageRepositoryImpl implements IMessageRepository {

    private final MessageMapper messageMapper;

    @Autowired
    public MessageRepositoryImpl(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public void save(Message message) {
        if (message.getMessageId() == null) {
            messageMapper.insert(message);
        } else {
            // 更新时用 wrapper 带上 conversationId 避免广播
            messageMapper.update(message,
                    new LambdaUpdateWrapper<Message>()
                            .eq(Message::getMessageId, message.getMessageId())
                            .eq(Message::getConversationId, message.getConversationId())
            );
        }
    }

    @Override
    public List<Message> findByConversationIdAndSeqAfter(Long conversationId, Long afterSeq, int limit) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .gt(Message::getSeq, afterSeq)
                        .orderByAsc(Message::getSeq)
                        .last("LIMIT " + limit)
        );
    }

    @Override
    public Message findById(Long messageId) {
        return messageMapper.selectById(messageId);
    }

    @Override
    public Message findByIdAndConversationId(Long messageId, Long conversationId) {
        return messageMapper.selectOne(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getMessageId, messageId)
                        .eq(Message::getConversationId, conversationId)
        );
    }

    @Override
    public void updateRevokeStatus(Long messageId) {
        messageMapper.update(null,
                new LambdaUpdateWrapper<Message>()
                        .eq(Message::getMessageId, messageId)
                        .set(Message::getIsRevoked, 1)
        );
    }

    @Override
    public void updateRevokeStatus(Long messageId, Long conversationId) {
        messageMapper.update(null,
                new LambdaUpdateWrapper<Message>()
                        .eq(Message::getMessageId, messageId)
                        .eq(Message::getConversationId, conversationId)
                        .set(Message::getIsRevoked, 1)
        );
    }

    @Override
    public void update(Message message) {
        messageMapper.update(message,
                new LambdaUpdateWrapper<Message>()
                        .eq(Message::getMessageId, message.getMessageId())
                        .eq(Message::getConversationId, message.getConversationId())
        );
    }
}
