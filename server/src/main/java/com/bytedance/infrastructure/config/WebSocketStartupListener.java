package com.bytedance.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.servlet.ServletContext;
import javax.websocket.server.ServerContainer;

/**
 * WebSocket 启动监听器，用于检查 WebSocket 端点是否被正确注册
 */
@Component
@Slf4j
public class WebSocketStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            ServletContext servletContext = event.getApplicationContext().getBean(ServletContext.class);
            if (servletContext == null) {
                log.error("========== WebSocket 检查失败: ServletContext 为 null ==========");
                return;
            }

            ServerContainer serverContainer = (ServerContainer) servletContext.getAttribute("javax.websocket.server.ServerContainer");
            if (serverContainer == null) {
                log.error("========== WebSocket 检查失败: ServerContainer 不可用 ==========");
                log.error("这可能是由于以下原因：");
                log.error("1. WebSocket 依赖未正确加载");
                log.error("2. 服务器容器不支持 WebSocket");
                log.error("3. WebSocket 配置未正确初始化");
                return;
            }

            log.info("========== WebSocket 检查成功 ==========");
            log.info("ServerContainer 类型: {}", serverContainer.getClass().getName());
            log.info("WebSocket 端点路径: /ws/{userId}");
            log.info("完整连接地址: ws://localhost:8081/ws/{userId}");
            log.info("示例: ws://localhost:8081/ws/1001");
            
            // 检查 ServerEndpointExporter 是否被创建
            try {
                Object exporter = event.getApplicationContext().getBean("serverEndpointExporter");
                if (exporter != null) {
                    log.info("ServerEndpointExporter Bean 已创建: {}", exporter.getClass().getName());
                }
            } catch (Exception e) {
                log.warn("ServerEndpointExporter Bean 未找到: {}", e.getMessage());
            }
            
            // 检查 WebSocketServer Bean 是否存在
            try {
                Object wsServer = event.getApplicationContext().getBean("webSocketServer");
                if (wsServer != null) {
                    log.info("WebSocketServer Bean 已创建: {}", wsServer.getClass().getName());
                }
            } catch (Exception e) {
                log.warn("WebSocketServer Bean 未找到: {}", e.getMessage());
            }
            
            log.info("=========================================");

        } catch (Exception e) {
            log.error("========== WebSocket 启动检查时发生异常 ==========", e);
        }
    }
}

