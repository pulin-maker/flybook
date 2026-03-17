package com.bytedance.usecase.conversation;

import com.bytedance.entity.Conversation;
import com.bytedance.entity.ConversationMember;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.repository.IConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 创建会话用例
 * 封装创建会话的业务逻辑
 */
@Component
public class CreateConversationUseCase {

    private final IConversationRepository conversationRepository;
    private final IConversationMemberRepository conversationMemberRepository;

    @Autowired
    public CreateConversationUseCase(IConversationRepository conversationRepository,
                                     IConversationMemberRepository conversationMemberRepository) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    /**
     * 执行创建会话逻辑
     * @return 会话ID
     */
    @Transactional(rollbackFor = Exception.class)
    public long execute(String name, Integer type, Long ownerId) {
        // 1. 创建会话
        Conversation conversation = Conversation.builder()
                .name(name)
                .type(type)
                .ownerId(ownerId)
                .currentSeq(0L)
                .createdTime(LocalDateTime.now())
                .build();
        conversationRepository.save(conversation);

        // 2. 把创建者（Owner）加入到成员表
        ConversationMember member = ConversationMember.builder()
                .conversationId(conversation.getConversationId())
                .userId(ownerId)
                .role(2) // 0=普通成员, 1=管理员, 2=群主
                .unreadCount(0) // 自己建的群，未读数是0
                .joinedTime(LocalDateTime.now())
                .build();
        conversationMemberRepository.save(member);

        return conversation.getConversationId();
    }
}

