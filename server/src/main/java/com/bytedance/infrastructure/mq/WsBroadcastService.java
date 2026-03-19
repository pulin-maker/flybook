package com.bytedance.infrastructure.mq;

import cn.hutool.json.JSONUtil;
import com.bytedance.infrastructure.websocket.AckPendingService;
import com.bytedance.infrastructure.websocket.SessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket 广播服务
 * 发送端：将推送消息发布到 Fanout Exchange，所有实例都会收到
 * 接收端：检查本地 SessionRegistry，只有持有用户 Session 的实例才执行推送
 */
@Component
@Slf4j
public class WsBroadcastService {

    private final RabbitTemplate rabbitTemplate;
    private final SessionRegistry sessionRegistry;
    private final AckPendingService ackPendingService;

    public WsBroadcastService(RabbitTemplate rabbitTemplate,
                               SessionRegistry sessionRegistry,
                               AckPendingService ackPendingService) {
        this.rabbitTemplate = rabbitTemplate;
        this.sessionRegistry = sessionRegistry;
        this.ackPendingService = ackPendingService;
    }

    /**
     * 广播推送：发送到 Fanout Exchange，所有实例都会收到
     */
    public void broadcast(Long userId, String pushJson, Long messageId, boolean requireAck) {
        WsPushMessage msg = WsPushMessage.builder()
                .userId(userId)
                .pushJson(pushJson)
                .messageId(messageId)
                .requireAck(requireAck)
                .build();

        String json = JSONUtil.toJsonStr(msg);
        rabbitTemplate.convertAndSend("flybook.ws.fanout", "", json);
        log.debug("广播推送: userId={}, messageId={}", userId, messageId);
    }

    /**
     * 简化版：不需要 ACK 追踪
     */
    public void broadcast(Long userId, String pushJson) {
        broadcast(userId, pushJson, null, false);
    }

    /**
     * 监听本实例的匿名队列（Fanout Exchange 广播）
     * 每个实例都会收到，但只有持有 Session 的实例才执行推送
     */
    @RabbitListener(queues = "#{wsFanoutQueue.name}")
    public void onBroadcast(String messageJson) {
        WsPushMessage msg;
        try {
            msg = JSONUtil.toBean(messageJson, WsPushMessage.class);
        } catch (Exception e) {
            log.error("广播消息反序列化失败: {}", messageJson, e);
            return;
        }

        boolean delivered = sessionRegistry.pushMessage(msg.getUserId(), msg.getPushJson());
        if (delivered) {
            log.debug("本地推送成功: userId={}, messageId={}", msg.getUserId(), msg.getMessageId());

            // 需要 ACK 追踪的消息注册 pending
            if (msg.isRequireAck() && msg.getMessageId() != null) {
                ackPendingService.registerPendingAck(msg.getMessageId(), msg.getUserId(), msg.getPushJson());
            }
        }
        // 用户不在本实例 → 静默忽略（其他实例会处理）
    }
}
