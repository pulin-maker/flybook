package com.bytedance.modules.message;

import lombok.Data;


@Data
public class MessageDTO {
    private Long conversationId;
    private String text;
}
