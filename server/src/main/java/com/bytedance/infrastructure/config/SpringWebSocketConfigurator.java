package com.bytedance.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.websocket.server.ServerEndpointConfig;

/**
 * WebSocket 配置器，用于支持 Spring 依赖注入
 * 
 * 这个配置器是必需的，因为 @ServerEndpoint 注解的类默认不能使用 Spring 的依赖注入。
 * 通过实现 ServerEndpointConfig.Configurator，我们可以让 WebSocket 端点从 Spring 容器中获取实例。
 */
@Component
@Slf4j
public class SpringWebSocketConfigurator extends ServerEndpointConfig.Configurator implements ApplicationContextAware {

    private static volatile BeanFactory context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringWebSocketConfigurator.context = applicationContext;
        log.info("SpringWebSocketConfigurator 初始化完成");
    }

    @Override
    public <T> T getEndpointInstance(Class<T> clazz) throws InstantiationException {
        try {
            log.debug("尝试从 Spring 容器获取 WebSocket 端点实例: {}", clazz.getName());
            T bean = context.getBean(clazz);
            log.debug("成功获取 WebSocket 端点实例: {}", clazz.getName());
            return bean;
        } catch (Exception e) {
            log.error("========== 无法从 Spring 容器获取 WebSocket 端点实例 ==========");
            log.error("端点类: {}", clazz.getName());
            log.error("错误类型: {}", e.getClass().getName());
            log.error("错误消息: {}", e.getMessage());
            log.error("完整错误堆栈:", e);
            log.error("=========================================");
            throw new InstantiationException("无法创建 WebSocket 端点实例: " + e.getMessage());
        }
    }
}
