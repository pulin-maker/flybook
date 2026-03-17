package com.bytedance.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.entity.MessageReaction;
import com.bytedance.mapper.MessageReactionMapper;
import com.bytedance.repository.IMessageReactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageReactionRepositoryImpl implements IMessageReactionRepository {

    private final MessageReactionMapper messageReactionMapper;

    @Autowired
    public MessageReactionRepositoryImpl(MessageReactionMapper messageReactionMapper) {
        this.messageReactionMapper = messageReactionMapper;
    }

    @Override
    public void save(MessageReaction reaction) {
        messageReactionMapper.insert(reaction);
    }

    @Override
    public void delete(Long conversationId, Long messageId, Long userId, String reactionType) {
        messageReactionMapper.delete(
                new LambdaQueryWrapper<MessageReaction>()
                        .eq(MessageReaction::getConversationId, conversationId)
                        .eq(MessageReaction::getMessageId, messageId)
                        .eq(MessageReaction::getUserId, userId)
                        .eq(MessageReaction::getReactionType, reactionType)
        );
    }

    @Override
    public List<MessageReaction> findByMessageId(Long conversationId, Long messageId) {
        return messageReactionMapper.selectList(
                new LambdaQueryWrapper<MessageReaction>()
                        .eq(MessageReaction::getConversationId, conversationId)
                        .eq(MessageReaction::getMessageId, messageId)
                        .orderByAsc(MessageReaction::getCreatedTime)
        );
    }

    @Override
    public boolean exists(Long conversationId, Long messageId, Long userId, String reactionType) {
        return messageReactionMapper.selectCount(
                new LambdaQueryWrapper<MessageReaction>()
                        .eq(MessageReaction::getConversationId, conversationId)
                        .eq(MessageReaction::getMessageId, messageId)
                        .eq(MessageReaction::getUserId, userId)
                        .eq(MessageReaction::getReactionType, reactionType)
        ) > 0;
    }
}
