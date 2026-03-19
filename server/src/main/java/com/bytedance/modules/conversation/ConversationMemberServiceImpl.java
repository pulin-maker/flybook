package com.bytedance.modules.conversation;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.modules.conversation.ConversationMember;
import com.bytedance.modules.conversation.ConversationMemberMapper;
import com.bytedance.modules.conversation.IConversationMemberService;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemberServiceImpl extends ServiceImpl<ConversationMemberMapper, ConversationMember>
        implements IConversationMemberService {
}
