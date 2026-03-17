package com.bytedance.usecase.conversation;

import com.bytedance.entity.Conversation;
import com.bytedance.entity.ConversationMember;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import com.bytedance.lock.DistributedLockService;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.repository.IConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 群组权限管理用例
 * 角色定义：0=普通成员, 1=管理员, 2=群主
 * 权限矩阵：
 *   群主(2)：可踢任何人、设管理员、转让群主、解散群、禁言
 *   管理员(1)：可踢普通成员、禁言普通成员
 *   普通成员(0)：无管理权限
 */
@Component
public class GroupPermissionUseCase {

    private static final int ROLE_MEMBER = 0;
    private static final int ROLE_ADMIN = 1;
    private static final int ROLE_OWNER = 2;

    private final IConversationRepository conversationRepository;
    private final IConversationMemberRepository conversationMemberRepository;
    private final DistributedLockService lockService;

    @Autowired
    public GroupPermissionUseCase(IConversationRepository conversationRepository,
                                  IConversationMemberRepository conversationMemberRepository,
                                  DistributedLockService lockService) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.lockService = lockService;
    }

    /**
     * 踢出成员
     * 群主可踢任何人，管理员只能踢普通成员
     */
    @Transactional(rollbackFor = Exception.class)
    public void kickMember(Long conversationId, Long operatorId, Long targetUserId) {
        Conversation conversation = requireGroupConversation(conversationId);
        ConversationMember operator = requireMember(conversationId, operatorId);
        ConversationMember target = requireMember(conversationId, targetUserId);

        // 不能踢自己
        if (operatorId.equals(targetUserId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "不能踢出自己");
        }

        // 权限校验：操作者角色必须 > 目标角色
        if (operator.getRole() <= target.getRole()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        conversationMemberRepository.deleteByConversationIdAndUserId(conversationId, targetUserId);
    }

    /**
     * 设置/取消管理员
     * 仅群主可操作
     */
    @Transactional(rollbackFor = Exception.class)
    public void setAdmin(Long conversationId, Long operatorId, Long targetUserId, boolean isAdmin) {
        Conversation conversation = requireGroupConversation(conversationId);
        requireOwner(conversationId, operatorId);
        ConversationMember target = requireMember(conversationId, targetUserId);

        // 不能对自己操作
        if (operatorId.equals(targetUserId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "不能修改自己的角色");
        }

        int newRole = isAdmin ? ROLE_ADMIN : ROLE_MEMBER;
        conversationMemberRepository.updateRole(conversationId, targetUserId, newRole);
    }

    /**
     * 转让群主
     * 仅当前群主可操作
     */
    @Transactional(rollbackFor = Exception.class)
    public void transferOwner(Long conversationId, Long operatorId, Long newOwnerId) {
        lockService.executeWithLock("lock:group:" + conversationId, () -> {
            Conversation conversation = requireGroupConversation(conversationId);
            requireOwner(conversationId, operatorId);
            requireMember(conversationId, newOwnerId);

            if (operatorId.equals(newOwnerId)) {
                throw new BizException(ErrorCode.PERMISSION_DENIED, "您已经是群主");
            }

            conversationMemberRepository.updateRole(conversationId, newOwnerId, ROLE_OWNER);
            conversationMemberRepository.updateRole(conversationId, operatorId, ROLE_MEMBER);
            conversationRepository.updateOwnerId(conversationId, newOwnerId);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void dissolveGroup(Long conversationId, Long operatorId) {
        lockService.executeWithLock("lock:group:" + conversationId, () -> {
            Conversation conversation = requireGroupConversation(conversationId);
            requireOwner(conversationId, operatorId);

            conversationMemberRepository.deleteByConversationId(conversationId);
            conversationRepository.deleteById(conversationId);
        });
    }

    /**
     * 禁言/解除禁言
     * 群主可禁言任何人，管理员只能禁言普通成员
     */
    @Transactional(rollbackFor = Exception.class)
    public void muteUser(Long conversationId, Long operatorId, Long targetUserId, boolean isMuted) {
        Conversation conversation = requireGroupConversation(conversationId);
        ConversationMember operator = requireMember(conversationId, operatorId);
        ConversationMember target = requireMember(conversationId, targetUserId);

        if (operatorId.equals(targetUserId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "不能禁言自己");
        }

        // 操作者角色必须 > 目标角色
        if (operator.getRole() <= target.getRole()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        conversationMemberRepository.updateMuteStatus(conversationId, targetUserId, isMuted ? 1 : 0);
    }

    // ======== 内部校验方法 ========

    private Conversation requireGroupConversation(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId);
        if (conversation == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (conversation.getType() != 2) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "仅群聊支持此操作");
        }
        return conversation;
    }

    private ConversationMember requireMember(Long conversationId, Long userId) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId);
        if (member == null) {
            throw new BizException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
        return member;
    }

    private void requireOwner(Long conversationId, Long userId) {
        ConversationMember member = requireMember(conversationId, userId);
        if (member.getRole() != ROLE_OWNER) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "仅群主可执行此操作");
        }
    }
}
