package com.bytedance.modules.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bytedance.modules.user.LoginResult;
import com.bytedance.modules.user.RegisterRequest;
import com.bytedance.modules.user.User;

public interface IUserService extends IService<User> {
    void register(RegisterRequest request);
    LoginResult login(String username, String password);
}
