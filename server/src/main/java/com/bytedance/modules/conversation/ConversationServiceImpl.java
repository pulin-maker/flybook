package com.bytedance.modules.conversation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.modules.user.UserCacheService;
import com.bytedance.modules.conversation.Conversation;
import com.bytedance.modules.conversation.ConversationMember;
import com.bytedance.modules.user.User;
import com.bytedance.common.exception.BizException;
import com.bytedance.common.exception.ErrorCode;
import com.bytedance.infrastructure.concurrent.DistributedLockService;
import com.bytedance.modules.conversation.ConversationMapper;
import com.bytedance.modules.conversation.ConversationMemberMapper;
import com.bytedance.modules.user.UserMapper;
import com.bytedance.modules.conversation.IConversationService;
import com.bytedance.modules.message.IMessageService;
import com.bytedance.common.utils.RedisUtils;
import com.bytedance.modules.conversation.ConversationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements IConversationService {

    private static final int ROLE_MEMBER = 0;
    private static final int ROLE_ADMIN = 1;
    private static final int ROLE_OWNER = 2;

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final UserMapper userMapper;
    private final UserCacheService userCacheService;
    private final DistributedLockService lockService;
    private final IMessageService messageService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    public ConversationServiceImpl(ConversationMapper conversationMapper,
                                  ConversationMemberMapper conversationMemberMapper,
                                  UserMapper userMapper,
                                  UserCacheService userCacheService,
                                  DistributedLockService lockService,
                                  @Lazy IMessageService messageService) {
        this.conversationMapper = conversationMapper;
        this.conversationMemberMapper = conversationMemberMapper;
        this.userMapper = userMapper;
        this.userCacheService = userCacheService;
        this.lockService = lockService;
        this.messageService = messageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long createConversation(String name, Integer type, Long ownerId) {
        // 1. 创建会话
        Conversation conversation = Conversation.builder()
                .name(name)
                .type(type)
                .ownerId(ownerId)
                .currentSeq(0L)
                .createdTime(LocalDateTime.now())
                .build();
        conversationMapper.insert(conversation);

        // 2. 把创建者加入成员表
        ConversationMember member = ConversationMember.builder()
                .conversationId(conversation.getConversationId())
                .userId(ownerId)
                .role(ROLE_OWNER)
                .unreadCount(0)
                .joinedTime(LocalDateTime.now())
                .build();
        conversationMemberMapper.insert(member);

        return conversation.getConversationId();
    }

    @Override
    public List<ConversationVO> getConversationList(Long userId) {
        // 1. 查出我参与的所有会话关系
        List<ConversationMember> myMemberships = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getUserId, userId));

        if (myMemberships.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 提取 conversationId 列表
        List<Long> conversationIds = myMemberships.stream()
                .map(ConversationMember::getConversationId)
                .collect(Collectors.toList());

        // 3. 批量查出所有会话
        List<Conversation> conversations = conversationMapper.selectBatchIds(conversationIds);
        Map<Long, Conversation> convMap = conversations.stream()
                .collect(Collectors.toMap(Conversation::getConversationId, c -> c));

        // 4. N+1 修复：一次性批量查出所有单聊会话的成员
        List<Long> dmConversationIds = conversations.stream()
                .filter(c -> c.getType() == 1)
                .map(Conversation::getConversationId)
                .collect(Collectors.toList());

        Map<Long, List<ConversationMember>> dmMembersMap = new HashMap<>();
        if (!dmConversationIds.isEmpty()) {
            List<ConversationMember> allDmMembers = conversationMemberMapper.selectList(
                    new LambdaQueryWrapper<ConversationMember>()
                            .in(ConversationMember::getConversationId, dmConversationIds));
            dmMembersMap = allDmMembers.stream()
                    .collect(Collectors.groupingBy(ConversationMember::getConversationId));
        }

        // 5. 组装 VO
        List<ConversationVO> voList = new ArrayList<>();

        for (ConversationMember myMember : myMemberships) {
            Conversation conv = convMap.get(myMember.getConversationId());
            if (conv == null) continue;

            String showName = conv.getName();
            String showAvatar = conv.getAvatarUrl();

            // 单聊：取对方用户信息
            if (conv.getType() == 1) {
                List<ConversationMember> members = dmMembersMap
                        .getOrDefault(conv.getConversationId(), Collections.emptyList());

                Long targetId = null;
                for (ConversationMember m : members) {
                    if (!m.getUserId().equals(userId)) {
                        targetId = m.getUserId();
                        break;
                    }
                }

                if (targetId != null) {
                    User targetUser = userCacheService.getById(targetId);
                    if (targetUser != null) {
                        showName = targetUser.getUsername();
                        showAvatar = targetUser.getAvatarUrl();
                    }
                }
            }

            ConversationVO vo = ConversationVO.builder()
                    .conversationId(conv.getConversationId())
                    .type(conv.getType())
                    .name(showName)
                    .avatarUrl(showAvatar)
                    .lastMsgContent(conv.getLastMsgContent())
                    .lastMsgTime(conv.getLastMsgTime())
                    .unreadCount(myMember.getUnreadCount())
                    .isTop(myMember.getIsTop() != null && myMember.getIsTop())
                    .build();

            voList.add(vo);
        }

        // 6. 排序 (置顶优先，其次按时间倒序)
        voList.sort((a, b) -> {
            boolean topA = Boolean.TRUE.equals(a.getIsTop());
            boolean topB = Boolean.TRUE.equals(b.getIsTop());

            if (topA && !topB) return -1;
            if (!topA && topB) return 1;

            LocalDateTime timeA = a.getLastMsgTime();
            LocalDateTime timeB = b.getLastMsgTime();

            if (timeB == null) return -1;
            if (timeA == null) return 1;
            return timeB.compareTo(timeA);
        });

        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMembers(Long conversationId, List<Long> targetUserIds, Long inviterId) {
        // 1. 校验会话
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return;
        }

        // 2. 过滤已在群里的人
        List<ConversationMember> existingMembers = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId));

        Set<Long> existingUserIds = existingMembers.stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        List<Long> effectiveUserIds = targetUserIds.stream()
                .distinct()
                .filter(uid -> !existingUserIds.contains(uid))
                .collect(Collectors.toList());

        if (effectiveUserIds.isEmpty()) {
            return;
        }

        // 3. 单聊人数限制
        if (conversation.getType() == 1) {
            int currentCount = existingMembers.size();
            int addCount = effectiveUserIds.size();
            if (currentCount + addCount > 2) {
                throw new BizException(ErrorCode.DM_MEMBER_LIMIT);
            }
        }

        // 4. 批量插入成员
        for (Long userId : effectiveUserIds) {
            ConversationMember member = ConversationMember.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .role(ROLE_MEMBER)
                    .unreadCount(0)
                    .joinedTime(LocalDateTime.now())
                    .build();
            conversationMemberMapper.insert(member);
        }

        // 5. 群聊发送系统通知
        if (conversation.getType() == 2) {
            User inviter = userMapper.selectById(inviterId);
            List<User> newUsers = userMapper.selectBatchIds(effectiveUserIds);
            String joinedNames = newUsers.stream()
                    .map(User::getUsername)
                    .collect(Collectors.joining("、"));

            String content = String.format("%s 邀请 %s 加入了群聊", inviter.getUsername(), joinedNames);
            messageService.sendMessage(conversationId, inviterId, 1,
                    "{\"text\":\"" + content + "\"}", null, null);
        }
    }

    @Override
    public Long findExistingConversation(String name, Integer type, List<Long> memberIds) {
        if (name == null || name.trim().isEmpty() || memberIds == null || memberIds.isEmpty()) {
            return null;
        }

        // 1. 根据群名和类型查询
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getName, name)
                        .eq(Conversation::getType, type));
        if (conversations.isEmpty()) {
            return null;
        }

        // 2. 比较成员列表
        Set<Long> targetMemberSet = new HashSet<>(memberIds);
        for (Conversation conversation : conversations) {
            List<ConversationMember> members = conversationMemberMapper.selectList(
                    new LambdaQueryWrapper<ConversationMember>()
                            .eq(ConversationMember::getConversationId, conversation.getConversationId()));

            Set<Long> existingMemberSet = members.stream()
                    .map(ConversationMember::getUserId)
                    .collect(Collectors.toSet());

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
                        .set(ConversationMember::getIsTop, isTop));
    }

    // ======== 群组权限管理 ========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickMember(Long conversationId, Long operatorId, Long targetUserId) {
        requireGroupConversation(conversationId);
        ConversationMember operator = requireMember(conversationId, operatorId);
        ConversationMember target = requireMember(conversationId, targetUserId);

        if (operatorId.equals(targetUserId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "不能踢出自己");
        }
        if (operator.getRole() <= target.getRole()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        conversationMemberMapper.delete(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, targetUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setAdmin(Long conversationId, Long operatorId, Long targetUserId, boolean isAdmin) {
        requireGroupConversation(conversationId);
        requireOwner(conversationId, operatorId);
        requireMember(conversationId, targetUserId);

        if (operatorId.equals(targetUserId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "不能修改自己的角色");
        }

        int newRole = isAdmin ? ROLE_ADMIN : ROLE_MEMBER;
        conversationMemberMapper.update(null,
                new LambdaUpdateWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, targetUserId)
                        .set(ConversationMember::getRole, newRole));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferOwner(Long conversationId, Long operatorId, Long newOwnerId) {
        lockService.executeWithLock("lock:group:" + conversationId, () -> {
            requireGroupConversation(conversationId);
            requireOwner(conversationId, operatorId);
            requireMember(conversationId, newOwnerId);

            if (operatorId.equals(newOwnerId)) {
                throw new BizException(ErrorCode.PERMISSION_DENIED, "您已经是群主");
            }

            conversationMemberMapper.update(null,
                    new LambdaUpdateWrapper<ConversationMember>()
                            .eq(ConversationMember::getConversationId, conversationId)
                            .eq(ConversationMember::getUserId, newOwnerId)
                            .set(ConversationMember::getRole, ROLE_OWNER));
            conversationMemberMapper.update(null,
                    new LambdaUpdateWrapper<ConversationMember>()
                            .eq(ConversationMember::getConversationId, conversationId)
                            .eq(ConversationMember::getUserId, operatorId)
                            .set(ConversationMember::getRole, ROLE_MEMBER));
            conversationMapper.update(null,
                    new LambdaUpdateWrapper<Conversation>()
                            .eq(Conversation::getConversationId, conversationId)
                            .set(Conversation::getOwnerId, newOwnerId));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolveGroup(Long conversationId, Long operatorId) {
        lockService.executeWithLock("lock:group:" + conversationId, () -> {
            requireGroupConversation(conversationId);
            requireOwner(conversationId, operatorId);

            conversationMemberMapper.delete(
                    new LambdaQueryWrapper<ConversationMember>()
                            .eq(ConversationMember::getConversationId, conversationId));
            conversationMapper.deleteById(conversationId);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void muteUser(Long conversationId, Long operatorId, Long targetUserId, boolean isMuted) {
        requireGroupConversation(conversationId);
        ConversationMember operator = requireMember(conversationId, operatorId);
        ConversationMember target = requireMember(conversationId, targetUserId);

        if (operatorId.equals(targetUserId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "不能禁言自己");
        }
        if (operator.getRole() <= target.getRole()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }

        conversationMemberMapper.update(null,
                new LambdaUpdateWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, targetUserId)
                        .set(ConversationMember::getIsMuted, isMuted ? 1 : 0));
    }

    // ======== 内部校验方法 ========

    private Conversation requireGroupConversation(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (conversation.getType() != 2) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "仅群聊支持此操作");
        }
        return conversation;
    }

    private ConversationMember requireMember(Long conversationId, Long userId) {
        ConversationMember member = conversationMemberMapper.selectOne(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, conversationId)
                        .eq(ConversationMember::getUserId, userId));
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
