package com.bytedance.modules.user;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String nickname; // 昵称 (可选，如果不填就默认和username一样)
}
