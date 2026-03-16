package com.bytedance.consumer;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bytedance.config.SpringWebSocketConfigurator;
import com.bytedance.websocket.AckPendingService;
import com.bytedance.websocket.SessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

/**
 * WebSocket 服务端
 * 使用 SessionRegistry 实现分布式会话管理
 * 支持客户端 ACK 确认机制
 */
@ServerEndpoint(value = "/ws/{userId}", configurator = SpringWebSocketConfigurator.class)
@Component
@Slf4j
public class WebSocketServer {

    private static final long MAX_IDLE_TIMEOUT = 60 * 1000L;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private AckPendingService ackPendingService;

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        try {
            session.setMaxIdleTimeout(MAX_IDLE_TIMEOUT);
            session.getUserProperties().put("userId", userId);
            sessionRegistry.register(userId, session);
        } catch (Exception e) {
            log.error("连接异常", e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        Long userId = (Long) session.getUserProperties().get("userId");
        if (userId != null) {
            sessionRegistry.unregister(userId);
        }
        log.info("用户 disconnected: {}, 原因: {}", userId, reason.getCloseCode());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if ("ping".equals(message)) {
            try {
                session.getBasicRemote().sendText("pong");
                Long userId = (Long) session.getUserProperties().get("userId");
                if (userId != null) {
                    sessionRegistry.refreshTtl(userId);
                }
                log.debug("收到心跳: ping -> pong");
            } catch (IOException e) {
                log.error("心跳回复失败", e);
            }
            return;
        }

        // 尝试解析为 ACK 消息: {"type":"ack","messageId":123}
        try {
            JSONObject json = JSONUtil.parseObj(message);
            if ("ack".equals(json.getStr("type"))) {
                Long messageId = json.getLong("messageId");
                Long userId = (Long) session.getUserProperties().get("userId");
                if (messageId != null && userId != null) {
                    ackPendingService.acknowledgeMessage(messageId, userId);
                }
                return;
            }
        } catch (Exception e) {
            // 非 JSON 或非 ACK 格式，作为普通业务消息处理
        }

        log.info("收到业务消息: {}", message);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 发生错误, Session ID: {}", (session != null ? session.getId() : "null"));
        log.error("错误详情:", error);
    }
}
