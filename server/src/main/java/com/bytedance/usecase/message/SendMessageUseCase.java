package com.bytedance.usecase.message;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.json.JSONUtil;
import com.bytedance.entity.Conversation;
import com.bytedance.entity.Message;
import com.bytedance.event.MessageSentEvent;
import com.bytedance.mq.MessageEventProducer;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.repository.IConversationRepository;
import com.bytedance.repository.IMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 发送消息用例
 * 封装发送消息的业务逻辑
 */
@Component
public class SendMessageUseCase {

    private final IMessageRepository messageRepository;
    private final IConversationRepository conversationRepository;
    private final IConversationMemberRepository conversationMemberRepository;
    private final Snowflake snowflake;
    private final MessageEventProducer messageEventProducer;

    @Autowired
    public SendMessageUseCase(IMessageRepository messageRepository,
                             IConversationRepository conversationRepository,
                             IConversationMemberRepository conversationMemberRepository,
                             Snowflake snowflake,
                             MessageEventProducer messageEventProducer) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.snowflake = snowflake;
        this.messageEventProducer = messageEventProducer;
    }

    /**
     * 执行发送消息逻辑
     * @return 保存的消息实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Message execute(Long conversationId, Long senderId, Integer msgType, String contentJson) {
        // 1. 查询 conversation_member 表，看该用户是否在会话中
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, senderId)) {
            throw new RuntimeException("您不是该会话成员，无法发送消息");
        }

        // 2. 查询会话（无锁），仅验证存在性
        Conversation conversation = conversationRepository.findById(conversationId);
        if (conversation == null) {
            throw new RuntimeException("会话不存在");
        }

        // 3. 使用 Snowflake 生成全局唯一、时间有序的序列号（无需行锁）
        long newSeq = snowflake.nextId();

        // 4. 构建消息
        LocalDateTime now = LocalDateTime.now();
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .seq(newSeq)
                .msgType(msgType)
                .content(contentJson)
                .createdTime(now)
                .build();

        messageRepository.save(message);

        // 5. CAS 更新会话摘要（仅当 newSeq > currentSeq 时才更新）
        String summary = "[未知消息]";
        if (msgType == 1) {
            summary = JSONUtil.parseObj(contentJson).getStr("text");
        } else if (msgType == 2) {
            summary = "[图片]";
        } else if (msgType == 5) {
            summary = "[待办任务]";
        }
        conversationRepository.casUpdateSeqAndSummary(conversationId, newSeq, summary, now);

        // 6. 更新未读数
        conversationMemberRepository.incrementUnreadCount(conversationId, senderId);

        // 7. 事务提交后异步推送（通过 RocketMQ 解耦）
        MessageSentEvent event = MessageSentEvent.builder()
                .messageId(message.getMessageId())
                .conversationId(conversationId)
                .senderId(senderId)
                .seq(newSeq)
                .msgType(msgType)
                .content(contentJson)
                .createdTime(now.toString())
                .build();
        messageEventProducer.sendAfterCommit(event);

        return message;
    }
}
