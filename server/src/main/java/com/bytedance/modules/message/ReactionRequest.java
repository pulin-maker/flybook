package com.bytedance.modules.message;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class ReactionRequest {
    @NotNull(message = "messageId 不能为空")
    private Long messageId;

    @NotBlank(message = "reactionType 不能为空")
    @Size(max = 32, message = "reactionType 最长 32 个字符")
    private String reactionType;
}
