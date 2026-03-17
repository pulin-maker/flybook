package com.bytedance.repository;

import com.bytedance.entity.MessageReaction;

import java.util.List;

public interface IMessageReactionRepository {

    void save(MessageReaction reaction);

    void delete(Long conversationId, Long messageId, Long userId, String reactionType);

    List<MessageReaction> findByMessageId(Long conversationId, Long messageId);

    boolean exists(Long conversationId, Long messageId, Long userId, String reactionType);
}
