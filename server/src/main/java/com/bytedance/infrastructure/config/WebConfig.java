package com.bytedance.infrastructure.config;

import com.bytedance.infrastructure.config.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Value("${storage.upload-path}")
    private String uploadPath;

    @Value("${storage.access-prefix}")
    private String accessPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 格式: /files/** -> file:D:/im-upload/
        // 注意: 这里的 file: 是必须的，表示文件系统协议
        registry.addResourceHandler(accessPrefix + "**")
                .addResourceLocations("file:" + uploadPath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/api/**") // 拦截所有 api 接口
                .excludePathPatterns(
                        "/api/users/login",    // 放行登录
                        "/api/users/register", // 放行注册
                        "/api/users/list"      // 放行获取用户列表（客户端需要同步用户数据）
                );
    }
}

