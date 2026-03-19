package com.bytedance.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * WebSocket 请求过滤器，用于记录所有 WebSocket 相关的请求
 */
@Component
@Order(1)
@Slf4j
public class WebSocketRequestFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        
        // 记录 WebSocket 相关的请求
        if (path.startsWith("/ws/")) {
            log.info("========== 检测到 WebSocket 请求 ==========");
            log.info("请求路径: {}", path);
            log.info("请求方法: {}", httpRequest.getMethod());
            log.info("Upgrade 头: {}", httpRequest.getHeader("Upgrade"));
            log.info("Connection 头: {}", httpRequest.getHeader("Connection"));
            log.info("Sec-WebSocket-Key 头: {}", httpRequest.getHeader("Sec-WebSocket-Key"));
            log.info("Sec-WebSocket-Version 头: {}", httpRequest.getHeader("Sec-WebSocket-Version"));
            log.info("=========================================");
        }
        
        chain.doFilter(request, response);
    }
}

