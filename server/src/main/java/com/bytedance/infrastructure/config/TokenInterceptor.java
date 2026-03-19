package com.bytedance.infrastructure.config;


import com.bytedance.infrastructure.config.AuthConfig;
import com.bytedance.common.utils.UserContext;
import com.bytedance.common.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthConfig authConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 如果是 OPTIONS 请求（跨域预检），直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 2. 如果关闭了登录系统，从请求头或参数中获取 userId
        if (!authConfig.isEnabled()) {
            Long userId = getUserIdFromRequest(request);
            if (userId != null) {
                UserContext.setUserId(userId);
                return true; // 放行
            } else {
                // 如果关闭登录但未提供 userId，返回 400 错误
                response.setStatus(400);
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 400, \"msg\": \"请提供 userId（请求头 X-User-Id 或参数 userId）\"}");
                return false;
            }
        }

        // 3. 启用登录系统时，从 Token 中获取 userId
        // 约定前端将 Token 放在 Header: "Authorization" 里面，格式通常是 "Bearer token字符串"
        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasLength(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim(); // 去掉 "Bearer " 前缀并去除空格
            
            // 检查token是否为空
            if (!StringUtils.hasLength(token)) {
                // token为空，直接返回401
                response.setStatus(401);
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 401, \"msg\": \"未登录或Token已过期\"}");
                return false;
            }

            // 解析 Token
            Long userId = jwtUtils.getUserIdFromToken(token);

            if (userId != null) {
                // 【核心】将 userId 放入当前线程上下文
                UserContext.setUserId(userId);
                return true; // 放行
            }
        }

        // 校验失败，返回 401
        response.setStatus(401);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\": 401, \"msg\": \"未登录或Token已过期\"}");
        return false; // 拦截
    }

    /**
     * 从请求中获取 userId（当关闭登录系统时使用）
     * 优先级：请求头 X-User-Id > 请求参数 userId
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        // 1. 优先从请求头 X-User-Id 获取
        String userIdHeader = request.getHeader("X-User-Id");
        if (StringUtils.hasLength(userIdHeader)) {
            try {
                return Long.parseLong(userIdHeader.trim());
            } catch (NumberFormatException e) {
                // 忽略格式错误，继续尝试其他方式
            }
        }

        // 2. 从请求参数 userId 获取
        String userIdParam = request.getParameter("userId");
        if (StringUtils.hasLength(userIdParam)) {
            try {
                return Long.parseLong(userIdParam.trim());
            } catch (NumberFormatException e) {
                // 忽略格式错误
            }
        }

        return null;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 6. 【非常重要】请求结束后清理 ThreadLocal，防止内存泄漏
        UserContext.clear();
    }
}
