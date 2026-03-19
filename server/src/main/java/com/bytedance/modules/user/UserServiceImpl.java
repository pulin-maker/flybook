package com.bytedance.modules.user;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.modules.user.LoginResult;
import com.bytedance.modules.user.RegisterRequest;
import com.bytedance.modules.user.User;
import com.bytedance.common.exception.BizException;
import com.bytedance.common.exception.ErrorCode;
import com.bytedance.modules.user.UserMapper;
import com.bytedance.modules.user.IUserService;
import com.bytedance.common.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;

    @Autowired
    public UserServiceImpl(UserMapper userMapper, JwtUtils jwtUtils) {
        this.userMapper = userMapper;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void register(RegisterRequest request) {
        // 1. 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (count != null && count > 0) {
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
        userMapper.insert(user);
    }

    @Override
    public LoginResult login(String username, String password) {
        // 1. 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 校验密码
        String userPassword = user.getPassword();
        if (StrUtil.isBlank(userPassword)) {
            // 测试用户没有密码，允许任意密码登录
        } else {
            if (!BCrypt.checkpw(password, userPassword)) {
                throw new BizException(ErrorCode.PASSWORD_WRONG);
            }
        }

        // 3. 生成 Token
        String token = jwtUtils.createToken(user.getUserId());

        return new LoginResult(token, user);
    }
}
