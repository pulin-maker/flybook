package com.bytedance.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.entity.ConversationMember;
import com.bytedance.event.MessageEditedEvent;
import com.bytedance.repository.IConversationMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息编辑事件消费者
 * 通知所有会话成员消息被编辑
 */
@Component
@Slf4j
public class MessageEditConsumer {

    private final IConversationMemberRepository conversationMemberRepository;
    private final WsBroadcastService wsBroadcastService;

    public MessageEditConsumer(IConversationMemberRepository conversationMemberRepository,
                               WsBroadcastService wsBroadcastService) {
        this.conversationMemberRepository = conversationMemberRepository;
        this.wsBroadcastService = wsBroadcastService;
    }

    @RabbitListener(queues = QueueNames.EDIT, containerFactory = "retryContainerFactory")
    public void onMessage(String eventJson) {
        MessageEditedEvent event;
        try {
            event = JSONUtil.toBean(eventJson, MessageEditedEvent.class);
        } catch (Exception e) {
            log.error("编辑事件反序列化失败: {}", eventJson, e);
            return;
        }

        log.debug("消费编辑事件: messageId={}", event.getMessageId());

        String pushJson = JSONUtil.createObj()
                .set("type", "edit")
                .set("messageId", event.getMessageId())
                .set("conversationId", event.getConversationId())
                .set("senderId", event.getSenderId())
                .set("newContent", event.getNewContent())
                .set("editTime", event.getEditTime())
                .toString();

        List<ConversationMember> members = conversationMemberRepository
                .findByConversationId(event.getConversationId());
        for (ConversationMember member : members) {
            wsBroadcastService.broadcast(member.getUserId(), pushJson);
        }
    }
}
