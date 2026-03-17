package com.bytedance.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bytedance.entity.Conversation;
import com.bytedance.mapper.ConversationMapper;
import com.bytedance.repository.IConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话数据访问实现（MySQL）
 */
@Repository
public class ConversationRepositoryImpl implements IConversationRepository {

    private final ConversationMapper conversationMapper;

    @Autowired
    public ConversationRepositoryImpl(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @Override
    public Conversation findById(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    @Override
    public List<Conversation> findByIds(List<Long> conversationIds) {
        return conversationMapper.selectBatchIds(conversationIds);
    }

    @Override
    public void save(Conversation conversation) {
        if (conversation.getConversationId() == null) {
            conversationMapper.insert(conversation);
        } else {
            conversationMapper.updateById(conversation);
        }
    }

    @Override
    public Conversation findByIdForUpdate(Long conversationId) {
        return conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getConversationId, conversationId)
                        .last("FOR UPDATE")
        );
    }

    @Override
    public void update(Conversation conversation) {
        conversationMapper.updateById(conversation);
    }

    @Override
    public List<Conversation> findByNameAndType(String name, Integer type) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getName, name)
                        .eq(Conversation::getType, type)
        );
    }

    @Override
    public boolean casUpdateSeqAndSummary(Long conversationId, Long newSeq, String lastMsgContent, java.time.LocalDateTime lastMsgTime) {
        int rows = conversationMapper.casUpdateSeqAndSummary(conversationId, newSeq, lastMsgContent, lastMsgTime);
        return rows > 0;
    }

    @Override
    public void updateOwnerId(Long conversationId, Long newOwnerId) {
        conversationMapper.update(null,
                new LambdaUpdateWrapper<Conversation>()
                        .eq(Conversation::getConversationId, conversationId)
                        .set(Conversation::getOwnerId, newOwnerId)
        );
    }

    @Override
    public void deleteById(Long conversationId) {
        conversationMapper.deleteById(conversationId);
    }
}

