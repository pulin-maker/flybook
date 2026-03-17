package com.bytedance.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.entity.ConversationMember;
import com.bytedance.event.MessageRevokedEvent;
import com.bytedance.repository.IConversationMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息撤回事件消费者
 * 通知所有会话成员消息被撤回
 */
@Component
@Slf4j
public class MessageRevokeConsumer {

    private final IConversationMemberRepository conversationMemberRepository;
    private final WsBroadcastService wsBroadcastService;

    public MessageRevokeConsumer(IConversationMemberRepository conversationMemberRepository,
                                 WsBroadcastService wsBroadcastService) {
        this.conversationMemberRepository = conversationMemberRepository;
        this.wsBroadcastService = wsBroadcastService;
    }

    @RabbitListener(queues = QueueNames.REVOKE, containerFactory = "retryContainerFactory")
    public void onMessage(String eventJson) {
        MessageRevokedEvent event;
        try {
            event = JSONUtil.toBean(eventJson, MessageRevokedEvent.class);
        } catch (Exception e) {
            log.error("撤回事件反序列化失败: {}", eventJson, e);
            return;
        }

        log.debug("消费撤回事件: messageId={}", event.getMessageId());

        String pushJson = JSONUtil.createObj()
                .set("type", "revoke")
                .set("messageId", event.getMessageId())
                .set("conversationId", event.getConversationId())
                .set("operatorId", event.getOperatorId())
                .toString();

        List<ConversationMember> members = conversationMemberRepository
                .findByConversationId(event.getConversationId());
        for (ConversationMember member : members) {
            wsBroadcastService.broadcast(member.getUserId(), pushJson);
        }
    }
}
