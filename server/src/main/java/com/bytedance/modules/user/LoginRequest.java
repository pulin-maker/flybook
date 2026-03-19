package com.bytedance.modules.user;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}

