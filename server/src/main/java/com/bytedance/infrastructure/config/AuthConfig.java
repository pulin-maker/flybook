package com.bytedance.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 认证配置类
 */
@Configuration
@ConfigurationProperties(prefix = "auth")
@Data
public class AuthConfig {
    /**
     * 是否启用登录系统
     * true: 需要登录，使用 Token 验证
     * false: 关闭登录，请求中附带 userId 即可
     */
    private boolean enabled = true;
}

