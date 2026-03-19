package com.bytedance.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Slf4j
public class WebSocketConfig {

    /**
     * 这个 Bean 会自动注册使用了 @ServerEndpoint 注解声明的 WebSocket endpoint
     * ServerEndpointExporter 会在应用启动后自动扫描并注册所有 @ServerEndpoint 注解的类
     * 
     * 注意：移除了 @Conditional 注解，因为在 Spring Boot 的 Servlet 环境中，
     * ServerEndpointExporter 应该总是可用的。如果 ServerContainer 不可用，
     * ServerEndpointExporter 会在注册端点时抛出异常，但不会影响 Bean 的创建。
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        log.info("========== 创建 ServerEndpointExporter，开始注册 WebSocket 端点 ==========");
        ServerEndpointExporter exporter = new ServerEndpointExporter();
        log.info("ServerEndpointExporter 实例已创建");
        log.info("注意: ServerEndpointExporter 会自动扫描并注册所有 @ServerEndpoint 注解的类");
        log.info("期望注册的端点: com.bytedance.infrastructure.websocket.WebSocketServer -> /ws/{userId}");
        log.info("========== ServerEndpointExporter 创建完成 ==========");
        return exporter;
    }
}
