package com.bytedance.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class MuteUserRequest {
    @NotNull(message = "conversationId 不能为空")
    private Long conversationId;
    @NotNull(message = "targetUserId 不能为空")
    private Long targetUserId;
    @NotNull(message = "isMuted 不能为空")
    private Boolean isMuted; // true=禁言, false=解除禁言
}
