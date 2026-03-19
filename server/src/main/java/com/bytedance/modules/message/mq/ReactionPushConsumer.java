package com.bytedance.modules.message.mq;
import com.bytedance.infrastructure.mq.QueueNames;
import com.bytedance.infrastructure.mq.WsBroadcastService;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bytedance.modules.conversation.ConversationMember;
import com.bytedance.modules.message.event.ReactionChangedEvent;
import com.bytedance.modules.conversation.ConversationMemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ReactionPushConsumer {

    private final ConversationMemberMapper conversationMemberMapper;
    private final WsBroadcastService wsBroadcastService;

    public ReactionPushConsumer(ConversationMemberMapper conversationMemberMapper,
                                WsBroadcastService wsBroadcastService) {
        this.conversationMemberMapper = conversationMemberMapper;
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

        List<ConversationMember> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMember>()
                        .eq(ConversationMember::getConversationId, event.getConversationId()));
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(event.getUserId())) {
                wsBroadcastService.broadcast(member.getUserId(), pushJson);
            }
        }
    }
}
