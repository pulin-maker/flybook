package com.bytedance.modules.conversation;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class KickMemberRequest {
    @NotNull(message = "conversationId 不能为空")
    private Long conversationId;
    @NotNull(message = "targetUserId 不能为空")
    private Long targetUserId;
}
