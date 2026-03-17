package com.bytedance.health;

import com.bytedance.websocket.SessionRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * WebSocket 连接数健康指示器
 */
@Component
public class WebSocketHealthIndicator implements HealthIndicator {

    private final SessionRegistry sessionRegistry;

    public WebSocketHealthIndicator(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Health health() {
        int onlineCount = sessionRegistry.getLocalOnlineCount();
        return Health.up()
                .withDetail("onlineUsers", onlineCount)
                .build();
    }
}
