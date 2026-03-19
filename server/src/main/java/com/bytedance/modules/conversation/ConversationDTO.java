package com.bytedance.modules.conversation;

import lombok.Data;

import java.util.List;

// DTO
@Data
public class ConversationDTO {
    private String name;
    private Integer type;
    private Long ownerId;
    private Long userId; // 可选的用户ID（当关闭登录系统时使用）
    private List<Long> targetUserIds; // 创建群聊时的成员列表（不包括创建者）
}
