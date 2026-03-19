package com.bytedance.modules.message.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEditedEvent {
    private Long messageId;
    private Long conversationId;
    private Long senderId;
    private String newContent;
    private String editTime;
}
