package com.bytedance.usecase.user;

import cn.hutool.crypto.digest.BCrypt;
import com.bytedance.dto.RegisterRequest;
import com.bytedance.entity.User;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import com.bytedance.repository.IUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 注册用户用例
 * 封装用户注册的业务逻辑
 */
@Component
public class RegisterUserUseCase {

    private final IUserRepository userRepository;

    @Autowired
    public RegisterUserUseCase(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 执行注册逻辑
     */
    public void execute(RegisterRequest request) {
        // 1. 业务校验：检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BizException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        // 2. 密码加密
        String encryptedPwd = BCrypt.hashpw(request.getPassword());

        // 3. 构建新用户
        User user = User.builder()
                .username(request.getUsername())
                .password(encryptedPwd)
                .avatarUrl("https://lf-flow-web-cdn.doubao.com/obj/flow-doubao/doubao/chat/static/image/logo-icon-white-bg.72df0b1a.png")
                .createdTime(LocalDateTime.now())
                .build();

        // 4. 保存用户
        userRepository.save(user);
    }
}

