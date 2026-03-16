package com.bytedance.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.entity.ConversationMember;
import com.bytedance.event.MessageSentEvent;
import com.bytedance.repository.IConversationMemberRepository;
import com.bytedance.websocket.AckPendingService;
import com.bytedance.websocket.SessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息推送消费者
 * 从 RocketMQ 消费消息事件，通过 WebSocket 推送给在线用户
 * 推送成功后注册 pending ACK，等待客户端确认
 */
@Component
@RocketMQMessageListener(
        topic = "${flybook.mq.topic:flybook-message-push}",
        consumerGroup = "${flybook.mq.consumer-group:flybook-push-consumer-group}"
)
@Slf4j
public class MessagePushConsumer implements RocketMQListener<String> {

    private final IConversationMemberRepository conversationMemberRepository;
    private final SessionRegistry sessionRegistry;
    private final AckPendingService ackPendingService;

    public MessagePushConsumer(IConversationMemberRepository conversationMemberRepository,
                               SessionRegistry sessionRegistry,
                               AckPendingService ackPendingService) {
        this.conversationMemberRepository = conversationMemberRepository;
        this.sessionRegistry = sessionRegistry;
        this.ackPendingService = ackPendingService;
    }

    @Override
    public void onMessage(String eventJson) {
        MessageSentEvent event;
        try {
            event = JSONUtil.toBean(eventJson, MessageSentEvent.class);
        } catch (Exception e) {
            log.error("MQ 消息反序列化失败: {}", eventJson, e);
            return;
        }

        log.debug("MQ 消费消息: messageId={}, conversationId={}", event.getMessageId(), event.getConversationId());

        // 1. 查询会话所有成员
        List<ConversationMember> members = conversationMemberRepository
                .findByConversationId(event.getConversationId());

        // 2. 构建推送 JSON（与原 Message 实体格式一致）
        String pushJson = buildPushJson(event);

        // 3. 推送给每个在线成员（排除发送者）
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(event.getSenderId())) {
                boolean delivered = sessionRegistry.pushMessage(member.getUserId(), pushJson);
                if (delivered) {
                    // 推送成功，注册 pending ACK，等待客户端确认
                    ackPendingService.registerPendingAck(event.getMessageId(), member.getUserId(), pushJson);
                    log.debug("推送成功: userId={}, messageId={}", member.getUserId(), event.getMessageId());
                } else {
                    log.debug("用户不在线: userId={}, 消息将通过 sync API 拉取", member.getUserId());
                }
            }
        }
    }

    /**
     * 构建与 Message 实体 JSON 格式一致的推送内容
     */
    private String buildPushJson(MessageSentEvent event) {
        return JSONUtil.createObj()
                .set("messageId", event.getMessageId())
                .set("conversationId", event.getConversationId())
                .set("senderId", event.getSenderId())
                .set("seq", event.getSeq())
                .set("msgType", event.getMsgType())
                .set("content", event.getContent())
                .set("createdTime", event.getCreatedTime())
                .toString();
    }
}
