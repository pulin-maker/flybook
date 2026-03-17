package com.bytedance.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.entity.ConversationMember;
import com.bytedance.event.ReactionChangedEvent;
import com.bytedance.repository.IConversationMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 表情回应变更事件消费者
 * 通知所有会话成员表情回应变更
 */
@Component
@Slf4j
public class ReactionPushConsumer {

    private final IConversationMemberRepository conversationMemberRepository;
    private final WsBroadcastService wsBroadcastService;

    public ReactionPushConsumer(IConversationMemberRepository conversationMemberRepository,
                                WsBroadcastService wsBroadcastService) {
        this.conversationMemberRepository = conversationMemberRepository;
        this.wsBroadcastService = wsBroadcastService;
    }

    @RabbitListener(queues = QueueNames.REACTION, containerFactory = "retryContainerFactory")
    public void onMessage(String eventJson) {
        ReactionChangedEvent event;
        try {
            event = JSONUtil.toBean(eventJson, ReactionChangedEvent.class);
        } catch (Exception e) {
            log.error("表情事件反序列化失败: {}", eventJson, e);
            return;
        }

        log.debug("消费表情事件: messageId={}, reactionType={}, added={}",
                event.getMessageId(), event.getReactionType(), event.isAdded());

        String pushJson = JSONUtil.createObj()
                .set("type", "reaction")
                .set("messageId", event.getMessageId())
                .set("conversationId", event.getConversationId())
                .set("userId", event.getUserId())
                .set("reactionType", event.getReactionType())
                .set("added", event.isAdded())
                .toString();

        List<ConversationMember> members = conversationMemberRepository
                .findByConversationId(event.getConversationId());
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(event.getUserId())) {
                wsBroadcastService.broadcast(member.getUserId(), pushJson);
            }
        }
    }
}
