package com.bytedance.modules.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long userId;

    private String username;

    private String avatarUrl;

    private String password;

    // 数据库已配置 DEFAULT CURRENT_TIMESTAMP，插入时可为 null
    private LocalDateTime createdTime;
}
