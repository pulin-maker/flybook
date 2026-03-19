package com.bytedance.modules.user;

import com.bytedance.modules.user.User;

public class LoginResult {
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
