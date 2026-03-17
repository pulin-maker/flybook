package com.bytedance.tracing;

import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * TraceId 链路追踪过滤器
 * 为每个请求生成唯一 traceId，放入 MDC 和响应头
 * 日志中通过 %X{traceId} 输出
 */
@Component
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID = "traceId";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 优先从请求头获取（上游服务传播）
        String traceId = httpRequest.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        MDC.put(TRACE_ID, traceId);
        httpResponse.setHeader(HEADER_TRACE_ID, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
