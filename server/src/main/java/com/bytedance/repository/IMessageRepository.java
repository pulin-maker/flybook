package com.bytedance.repository;

import com.bytedance.entity.Message;

import java.util.List;

/**
 * 消息数据访问接口
 */
public interface IMessageRepository {
    void save(Message message);

    List<Message> findByConversationIdAndSeqAfter(Long conversationId, Long afterSeq, int limit);

    /**
     * 根据消息ID查找（分库分表场景会广播所有分片）
     */
    Message findById(Long messageId);

    /**
     * 根据消息ID + 分片键查找（精确路由，不广播）
     */
    Message findByIdAndConversationId(Long messageId, Long conversationId);

    /**
     * 标记消息为已撤回（广播）
     */
    void updateRevokeStatus(Long messageId);

    /**
     * 标记消息为已撤回（精确路由）
     */
    void updateRevokeStatus(Long messageId, Long conversationId);

    /**
     * 更新消息（用于编辑等场景）
     */
    void update(Message message);
}
