package com.bytedance.modules.conversation;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class SetAdminRequest {
    @NotNull(message = "conversationId 不能为空")
    private Long conversationId;
    @NotNull(message = "targetUserId 不能为空")
    private Long targetUserId;
    @NotNull(message = "isAdmin 不能为空")
    private Boolean isAdmin; // true=设为管理员, false=取消管理员
}
