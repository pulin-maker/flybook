package com.bytedance.usecase.message;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.json.JSONUtil;
import com.bytedance.cache.ConversationCacheService;
import com.bytedance.entity.Conversation;
import com.bytedance.entity.ConversationMember;
import com.bytedance.entity.Message;
import com.bytedance.entity.MqOutbox;
import com.bytedance.event.MessageSentEvent;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import com.bytedance.mapper.MqOutboxMapper;
import com.bytedance.mq.MessageEventProducer;
import com.bytedance.mq.RoutingKeys;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.repository.IConversationRepository;
import com.bytedance.repository.IMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
    private final MqOutboxMapper mqOutboxMapper;
    private final ConversationCacheService conversationCacheService;

    @Value("${flybook.mq.exchange:flybook.message}")
    private String mqExchange;

    @Autowired
    public SendMessageUseCase(IMessageRepository messageRepository,
                             IConversationRepository conversationRepository,
                             IConversationMemberRepository conversationMemberRepository,
                             Snowflake snowflake,
                             MessageEventProducer messageEventProducer,
                             MqOutboxMapper mqOutboxMapper,
                             ConversationCacheService conversationCacheService) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.snowflake = snowflake;
        this.messageEventProducer = messageEventProducer;
        this.mqOutboxMapper = mqOutboxMapper;
        this.conversationCacheService = conversationCacheService;
    }

    /**
     * 执行发送消息逻辑
     * @return 保存的消息实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Message execute(Long conversationId, Long senderId, Integer msgType, String contentJson,
                           List<Long> mentionUserIds, Long quoteId) {
        // 1. 查询成员关系，校验是否在会话中
        ConversationMember senderMember = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, senderId);
        if (senderMember == null) {
            throw new BizException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }

        // 2. 禁言检查
        if (senderMember.getIsMuted() != null && senderMember.getIsMuted() == 1) {
            throw new BizException(ErrorCode.USER_IS_MUTED);
        }

        // 3. 查询会话（无锁），仅验证存在性
        Conversation conversation = conversationRepository.findById(conversationId);
        if (conversation == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        // 3. 使用 Snowflake 生成全局唯一、时间有序的序列号（无需行锁）
        long newSeq = snowflake.nextId();

        // 4. 序列化 mentions
        String mentionsJson = null;
        if (mentionUserIds != null && !mentionUserIds.isEmpty()) {
            mentionsJson = JSONUtil.toJsonStr(mentionUserIds);
        }

        // 5. 构建消息
        LocalDateTime now = LocalDateTime.now();
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .seq(newSeq)
                .msgType(msgType)
                .content(contentJson)
                .mentions(mentionsJson)
                .quoteId(quoteId)
                .createdTime(now)
                .build();

        messageRepository.save(message);

        // 6. CAS 更新会话摘要（仅当 newSeq > currentSeq 时才更新）
        String summary = "[未知消息]";
        if (msgType == 1) {
            summary = JSONUtil.parseObj(contentJson).getStr("text");
        } else if (msgType == 2) {
            summary = "[图片]";
        } else if (msgType == 5) {
            summary = "[待办任务]";
        }
        conversationRepository.casUpdateSeqAndSummary(conversationId, newSeq, summary, now);

        // 淘汰会话缓存（摘要已变更）
        conversationCacheService.evict(conversationId);

        // 7. 更新未读数
        conversationMemberRepository.incrementUnreadCount(conversationId, senderId);

        // 8. 构建事件
        MessageSentEvent event = MessageSentEvent.builder()
                .messageId(message.getMessageId())
                .conversationId(conversationId)
                .senderId(senderId)
                .seq(newSeq)
                .msgType(msgType)
                .content(contentJson)
                .createdTime(now.toString())
                .mentions(mentionsJson)
                .quoteId(quoteId)
                .build();

        // 9. 在同一事务内写入 Outbox 表（保证双写一致性）
        MqOutbox outbox = MqOutbox.builder()
                .messageId(message.getMessageId())
                .exchange(mqExchange)
                .routingKey(RoutingKeys.MSG_SENT)
                .body(JSONUtil.toJsonStr(event))
                .status(MqOutbox.Status.PENDING)
                .retryCount(0)
                .build();
        mqOutboxMapper.insert(outbox);

        // 10. 事务提交后异步推送（通过 RabbitMQ 解耦）
        messageEventProducer.sendAfterCommit(event);

        return message;
    }
}
