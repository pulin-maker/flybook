package com.bytedance.infrastructure.config;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;

@Configuration
@Slf4j
public class ServerIdConfig {

    @Value("${flybook.server.id:}")
    private String configuredId;

    @Value("${server.port:8081}")
    private int serverPort;

    @Bean
    public String serverId() {
        if (configuredId != null && !configuredId.isEmpty()) {
            log.info("使用配置的 serverId: {}", configuredId);
            return configuredId;
        }
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }
        String id = hostname + ":" + serverPort + ":" + RandomUtil.randomString(4);
        log.info("自动生成 serverId: {}", id);
        return id;
    }
}
