package com.bytedance.modules.message.mq;
import com.bytedance.infrastructure.mq.QueueNames;
import com.bytedance.infrastructure.mq.WsBroadcastService;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.modules.conversation.ConversationMember;
import com.bytedance.modules.message.event.MessageEditedEvent;
import com.bytedance.modules.conversation.ConversationMemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MessageEditConsumer {

    private final ConversationMemberMapper conversationMemberMapper;
    private final WsBroadcastService wsBroadcastService;

    public MessageEditConsumer(ConversationMemberMapper conversationMemberMapper,
                               WsBroadcastService wsBroadcastService) {
        this.conversationMemberMapper = conversationMemberMapper;
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

        List<ConversationMember> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, event.getConversationId()));
        for (ConversationMember member : members) {
            wsBroadcastService.broadcast(member.getUserId(), pushJson);
        }
    }
}
