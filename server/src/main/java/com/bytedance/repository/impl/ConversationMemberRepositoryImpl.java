package com.bytedance.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bytedance.entity.ConversationMember;
import com.bytedance.mapper.ConversationMemberMapper;
import com.bytedance.repository.IConversationMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话成员数据访问实现（MySQL）
 */
@Repository
public class ConversationMemberRepositoryImpl implements IConversationMemberRepository {

    private final ConversationMemberMapper conversationMemberMapper;

    @Autowired
    public ConversationMemberRepositoryImpl(ConversationMemberMapper conversationMemberMapper) {
        this.conversationMemberMapper = conversationMemberMapper;
    }

    @Override
    public List<ConversationMember> findByConversationId(Long conversationId) {
        return conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
        );
    }

    @Override
    public List<ConversationMember> findByUserId(Long userId) {
        return conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getUserId, userId)
        );
    }

    @Override
    public boolean existsByConversationIdAndUserId(Long conversationId, Long userId) {
        Long count = conversationMemberMapper.selectCount(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId)
        );
        return count != null && count > 0;
    }

    @Override
    public void save(ConversationMember member) {
        if (member.getId() == null) {
            conversationMemberMapper.insert(member);
        } else {
            conversationMemberMapper.updateById(member);
        }
    }

    @Override
    public void saveBatch(List<ConversationMember> members) {
        for (ConversationMember member : members) {
            conversationMemberMapper.insert(member);
        }
    }

    @Override
    public void incrementUnreadCount(Long conversationId, Long senderId) {
        conversationMemberMapper.incrementUnreadCount(conversationId, senderId);
    }

    @Override
    public ConversationMember findByConversationIdAndUserId(Long conversationId, Long userId) {
        return conversationMemberMapper.selectOne(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId)
        );
    }

    @Override
    public void deleteByConversationIdAndUserId(Long conversationId, Long userId) {
        conversationMemberMapper.delete(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId)
        );
    }

    @Override
    public void updateRole(Long conversationId, Long userId, Integer role) {
        conversationMemberMapper.update(null,
                new LambdaUpdateWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId)
                        .set(ConversationMember::getRole, role)
        );
    }

    @Override
    public void deleteByConversationId(Long conversationId) {
        conversationMemberMapper.delete(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
        );
    }

    @Override
    public void updateMuteStatus(Long conversationId, Long userId, Integer isMuted) {
        conversationMemberMapper.update(null,
                new LambdaUpdateWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId)
                        .set(ConversationMember::getIsMuted, isMuted)
        );
    }

    @Override
    public List<ConversationMember> findByConversationIds(List<Long> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return List.of();
        }
        return conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .in(ConversationMember::getConversationId, conversationIds)
        );
    }
}

