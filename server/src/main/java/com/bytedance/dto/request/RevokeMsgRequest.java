package com.bytedance.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RevokeMsgRequest {
    @NotNull(message = "messageId 不能为空")
    private Long messageId;
}
