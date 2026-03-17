package com.bytedance.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.entity.Conversation;
import com.bytedance.entity.ConversationMember;
import com.bytedance.mapper.ConversationMapper;
import com.bytedance.mapper.ConversationMemberMapper;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.repository.IConversationRepository;
import com.bytedance.service.IConversationService;
import com.bytedance.usecase.conversation.AddMembersUseCase;
import com.bytedance.usecase.conversation.CreateConversationUseCase;
import com.bytedance.usecase.conversation.GetConversationListUseCase;
import com.bytedance.usecase.conversation.GroupPermissionUseCase;
import com.bytedance.vo.ConversationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bytedance.utils.RedisUtils;
import cn.hutool.json.JSONUtil; // 引入 Hutool JSON 工具
import java.util.concurrent.TimeUnit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话服务实现
 * 作为门面层，协调 UseCase
 */
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements IConversationService {

    private final CreateConversationUseCase createConversationUseCase;
    private final GetConversationListUseCase getConversationListUseCase;
    private final AddMembersUseCase addMembersUseCase;
    private final GroupPermissionUseCase groupPermissionUseCase;
    private final IConversationRepository conversationRepository;
    private final IConversationMemberRepository conversationMemberRepository;
    private final ConversationMemberMapper conversationMemberMapper;

    @Autowired
    public ConversationServiceImpl(CreateConversationUseCase createConversationUseCase,
                                  GetConversationListUseCase getConversationListUseCase,
                                  AddMembersUseCase addMembersUseCase,
                                  GroupPermissionUseCase groupPermissionUseCase,
                                  IConversationRepository conversationRepository,
                                  IConversationMemberRepository conversationMemberRepository,
                                  ConversationMemberMapper conversationMemberMapper) {
        this.createConversationUseCase = createConversationUseCase;
        this.getConversationListUseCase = getConversationListUseCase;
        this.addMembersUseCase = addMembersUseCase;
        this.groupPermissionUseCase = groupPermissionUseCase;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.conversationMemberMapper = conversationMemberMapper;
    }

    @Autowired
    private RedisUtils redisUtils; // 【注入 Redis 工具类】

    @Override
    public long createConversation(String name, Integer type, Long ownerId) {
        return createConversationUseCase.execute(name, type, ownerId);
    }

    @Override
    public List<ConversationVO> getConversationList(Long userId) {
        return getConversationListUseCase.execute(userId);
    }

    @Override
    public void addMembers(Long conversationId, List<Long> targetUserIds, Long inviterId) {
        addMembersUseCase.execute(conversationId, targetUserIds, inviterId);
    }

    @Override
    public Long findExistingConversation(String name, Integer type, List<Long> memberIds) {
        // 如果群名为空或成员列表为空，不进行查找
        if (name == null || name.trim().isEmpty() || memberIds == null || memberIds.isEmpty()) {
            return null;
        }

        // 1. 根据群名和类型查询所有群聊
        List<Conversation> conversations = conversationRepository.findByNameAndType(name, type);
        if (conversations.isEmpty()) {
            return null;
        }

        // 2. 将目标成员列表转换为 Set（去重并便于比较）
        Set<Long> targetMemberSet = new HashSet<>(memberIds);

        // 3. 遍历每个群聊，检查成员列表是否完全一致
        for (Conversation conversation : conversations) {
            List<ConversationMember> members = conversationMemberRepository
                    .findByConversationId(conversation.getConversationId());
            
            // 提取成员ID集合
            Set<Long> existingMemberSet = members.stream()
                    .map(ConversationMember::getUserId)
                    .collect(Collectors.toSet());

            // 比较两个集合是否完全一致（大小相同且包含所有元素）
            if (targetMemberSet.size() == existingMemberSet.size() 
                    && targetMemberSet.containsAll(existingMemberSet)) {
                return conversation.getConversationId();
            }
        }

        return null;
    }

    @Override
    public void clearUnreadCount(Long conversationId, Long userId) {
        conversationMemberMapper.clearUnreadCount(conversationId, userId);
    }

    @Override
    public void clearAllUnreadCount(Long userId) {
        conversationMemberMapper.clearAllUnreadCount(userId);
    }

    @Override
    public void setConversationTop(Long conversationId, Long userId, boolean isTop) {
        conversationMemberMapper.update(null,
                new LambdaUpdateWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId)
                        .set(ConversationMember::getIsTop, isTop)
        );
    }

    @Override
    public void kickMember(Long conversationId, Long operatorId, Long targetUserId) {
        groupPermissionUseCase.kickMember(conversationId, operatorId, targetUserId);
    }

    @Override
    public void setAdmin(Long conversationId, Long operatorId, Long targetUserId, boolean isAdmin) {
        groupPermissionUseCase.setAdmin(conversationId, operatorId, targetUserId, isAdmin);
    }

    @Override
    public void transferOwner(Long conversationId, Long operatorId, Long newOwnerId) {
        groupPermissionUseCase.transferOwner(conversationId, operatorId, newOwnerId);
    }

    @Override
    public void dissolveGroup(Long conversationId, Long operatorId) {
        groupPermissionUseCase.dissolveGroup(conversationId, operatorId);
    }

    @Override
    public void muteUser(Long conversationId, Long operatorId, Long targetUserId, boolean isMuted) {
        groupPermissionUseCase.muteUser(conversationId, operatorId, targetUserId, isMuted);
    }
}
