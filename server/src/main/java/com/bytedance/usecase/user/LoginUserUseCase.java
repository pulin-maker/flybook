package com.bytedance.usecase.user;

import cn.hutool.core.util.StrUtil;
import com.bytedance.entity.User;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import com.bytedance.repository.IUserRepository;
import com.bytedance.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户登录用例
 * 封装用户登录的业务逻辑
 */
@Component
public class LoginUserUseCase {

    private final IUserRepository userRepository;
    private final JwtUtils jwtUtils;

    @Autowired
    public LoginUserUseCase(IUserRepository userRepository, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
    }

    /**
     * 执行登录逻辑
     * @return 登录结果，包含 Token 和用户信息
     */
    public LoginResult execute(String username, String password) {
        // 1. 查询用户
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 校验密码
        // 如果用户没有设置密码（测试用户），允许空密码登录
        String userPassword = user.getPassword();
        if (StrUtil.isBlank(userPassword)) {
            // 测试用户没有密码，允许空密码或任意密码登录
            // 为了测试方便，允许任意密码
        } else {
            // 用户有密码，需要验证
            // 注意：这里为了演示，使用明文对比。实际应该使用 BCryptPasswordEncoder
            if (!userPassword.equals(password)) {
                throw new BizException(ErrorCode.PASSWORD_WRONG);
            }
        }

        // 3. 生成 Token
        String token = jwtUtils.createToken(user.getUserId());
        
        return new LoginResult(token, user);
    }
    
    /**
     * 登录结果
     */
    public static class LoginResult {
        private final String token;
        private final User user;
        
        public LoginResult(String token, User user) {
            this.token = token;
            this.user = user;
        }
        
        public String getToken() {
            return token;
        }
        
        public User getUser() {
            return user;
        }
    }
}

