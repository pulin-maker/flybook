package com.bytedance.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class TransferOwnerRequest {
    @NotNull(message = "conversationId 不能为空")
    private Long conversationId;
    @NotNull(message = "newOwnerId 不能为空")
    private Long newOwnerId;
}
