package com.bytedance.config;

import cn.hutool.core.lang.Snowflake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {

    @Value("${flybook.snowflake.worker-id:1}")
    private long workerId;

    @Value("${flybook.snowflake.datacenter-id:1}")
    private long datacenterId;

    @Bean
    public Snowflake snowflake() {
        return new Snowflake(workerId, datacenterId);
    }
}
