package com.bytedance.modules.message;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class EditMsgRequest {
    @NotNull(message = "messageId 不能为空")
    private Long messageId;

    @NotBlank(message = "消息内容不能为空")
    private String newContent;
}
