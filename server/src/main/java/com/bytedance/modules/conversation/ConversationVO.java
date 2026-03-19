package com.bytedance.modules.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {
    private Long conversationId;
    private Integer type;       // 1=单聊, 2=群聊
    private String name;        // 名称
    private String avatarUrl;   // 头像

    private String lastMsgContent; // 最后一条消息预览
    private LocalDateTime lastMsgTime; // 最后一条消息时间

    private Integer unreadCount;   // 未读数红点

    private Boolean isTop;
}
