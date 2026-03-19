package com.bytedance.modules.conversation;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class AddMemberRequest {
    private Long conversationId;
    private List<Long> targetUserIds; // 想要拉入群的用户 ID 列表
    private Long inviterId;           // 邀请人 ID (操作人)
}
