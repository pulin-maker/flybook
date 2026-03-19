package com.bytedance.modules.conversation;

import lombok.Data;

@Data
public class TopRequest {
    private Long conversationId;
    private Boolean isTop; // true=置顶, false=取消置顶
}
