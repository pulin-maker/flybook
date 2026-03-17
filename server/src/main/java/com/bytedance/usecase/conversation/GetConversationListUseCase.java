package com.bytedance.usecase.conversation;

import com.bytedance.cache.UserCacheService;
import com.bytedance.entity.Conversation;
import com.bytedance.entity.ConversationMember;
import com.bytedance.entity.User;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.repository.IConversationRepository;
import com.bytedance.vo.ConversationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 获取会话列表用例
 * 使用二级缓存（Caffeine + Redis）+ 批量查询修复 N+1
 */
@Component
public class GetConversationListUseCase {

    private final IConversationMemberRepository conversationMemberRepository;
    private final IConversationRepository conversationRepository;
    private final UserCacheService userCacheService;

    @Autowired
    public GetConversationListUseCase(IConversationMemberRepository conversationMemberRepository,
                                      IConversationRepository conversationRepository,
                                      UserCacheService userCacheService) {
        this.conversationMemberRepository = conversationMemberRepository;
        this.conversationRepository = conversationRepository;
        this.userCacheService = userCacheService;
    }

    public List<ConversationVO> execute(Long userId) {
        // 1. 查出我参与的所有会话关系
        List<ConversationMember> myMemberships = conversationMemberRepository.findByUserId(userId);

        if (myMemberships.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 提取 conversationId 列表
        List<Long> conversationIds = myMemberships.stream()
                .map(ConversationMember::getConversationId)
                .collect(Collectors.toList());

        // 3. 批量查出所有会话
        List<Conversation> conversations = conversationRepository.findByIds(conversationIds);
        Map<Long, Conversation> convMap = conversations.stream()
                .collect(Collectors.toMap(Conversation::getConversationId, c -> c));

        // 4. 【N+1 修复】一次性批量查出所有单聊会话的成员
        List<Long> dmConversationIds = conversations.stream()
                .filter(c -> c.getType() == 1)
                .map(Conversation::getConversationId)
                .collect(Collectors.toList());

        // conversationId -> 成员列表
        Map<Long, List<ConversationMember>> dmMembersMap = new HashMap<>();
        if (!dmConversationIds.isEmpty()) {
            List<ConversationMember> allDmMembers = conversationMemberRepository
                    .findByConversationIds(dmConversationIds);
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

            // 单聊：从批量查询结果中取对方用户信息
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
}
