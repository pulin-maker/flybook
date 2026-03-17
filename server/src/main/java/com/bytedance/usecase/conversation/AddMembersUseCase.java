package com.bytedance.usecase.conversation;

import com.bytedance.entity.Conversation;
import com.bytedance.entity.ConversationMember;
import com.bytedance.entity.User;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.repository.IConversationRepository;
import com.bytedance.repository.IUserRepository;
import com.bytedance.usecase.message.SendMessageUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 添加成员用例
 * 封装添加成员到会话的业务逻辑
 */
@Component
public class AddMembersUseCase {

    private final IConversationRepository conversationRepository;
    private final IConversationMemberRepository conversationMemberRepository;
    private final IUserRepository userRepository;
    private final SendMessageUseCase sendMessageUseCase;

    @Autowired
    public AddMembersUseCase(IConversationRepository conversationRepository,
                             IConversationMemberRepository conversationMemberRepository,
                             IUserRepository userRepository,
                             SendMessageUseCase sendMessageUseCase) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userRepository = userRepository;
        this.sendMessageUseCase = sendMessageUseCase;
    }

    /**
     * 执行添加成员逻辑
     */
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long conversationId, List<Long> targetUserIds, Long inviterId) {
        // 1. 校验会话
        Conversation conversation = conversationRepository.findById(conversationId);
        if (conversation == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        // 【优化点 1】: 这里先不判断人数，等下面过滤完去重后再判断，更严谨
        // 只是简单校验一下入参不为空
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return;
        }

        // 2. 过滤掉已经在群里的人 (去重)
        List<ConversationMember> existingMembers = conversationMemberRepository
                .findByConversationId(conversationId);

        Set<Long> existingUserIds = existingMembers.stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        // 计算出真正需要插入的有效用户 ID
        List<Long> effectiveUserIds = targetUserIds.stream()
                .distinct() // 去掉入参里的重复值 (比如前端传了两次 id:3)
                .filter(uid -> !existingUserIds.contains(uid)) // 去掉已经在群里的人
                .collect(Collectors.toList());

        if (effectiveUserIds.isEmpty()) {
            return; // 都在群里了，无需操作
        }

        // ========================================================
        // 【核心修改】: 校验单聊人数限制 (现有 + 新增 > 2 则报错)
        // ========================================================
        if (conversation.getType() == 1) {
            int currentCount = existingMembers.size(); // 数据库里已有的
            int addCount = effectiveUserIds.size();    // 即将插入的

            if (currentCount + addCount > 2) {
                // 提示语可以根据产品需求调整，比如提示"请创建新的群聊"
                throw new BizException(ErrorCode.DM_MEMBER_LIMIT);
            }
        }
        // ========================================================

        // 3. 批量插入成员
        List<ConversationMember> newMembers = new ArrayList<>();
        for (Long userId : effectiveUserIds) {
            ConversationMember member = ConversationMember.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .role(0)
                    .unreadCount(0)
                    .joinedTime(LocalDateTime.now())
                    .build();
            newMembers.add(member);
        }
        conversationMemberRepository.saveBatch(newMembers);

        // 4. 发送一条系统通知消息 群聊发信息，单聊不发信息。
        if (conversation.getType() == 2) {
            User inviter = userRepository.findById(inviterId);
            List<User> newUsers = userRepository.findByIds(effectiveUserIds);
            String joinedNames = newUsers.stream()
                    .map(User::getUsername)
                    .collect(Collectors.joining("、"));

            String content = String.format("%s 邀请 %s 加入了群聊", inviter.getUsername(), joinedNames);
            sendMessageUseCase.execute(conversationId, inviterId, 1,
                    "{\"text\":\"" + content + "\"}", null, null);
        }
    }
}

