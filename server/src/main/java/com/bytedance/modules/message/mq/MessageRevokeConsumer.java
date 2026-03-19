package com.bytedance.modules.message.mq;
import com.bytedance.infrastructure.mq.QueueNames;
import com.bytedance.infrastructure.mq.WsBroadcastService;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.modules.conversation.ConversationMember;
import com.bytedance.modules.message.event.MessageRevokedEvent;
import com.bytedance.modules.conversation.ConversationMemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MessageRevokeConsumer {

    private final ConversationMemberMapper conversationMemberMapper;
    private final WsBroadcastService wsBroadcastService;

    public MessageRevokeConsumer(ConversationMemberMapper conversationMemberMapper,
                                 WsBroadcastService wsBroadcastService) {
        this.conversationMemberMapper = conversationMemberMapper;
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

        List<ConversationMember> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, event.getConversationId()));
        for (ConversationMember member : members) {
            wsBroadcastService.broadcast(member.getUserId(), pushJson);
        }
    }
}
