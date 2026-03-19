package com.bytedance.modules.message.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionChangedEvent {
    private Long messageId;
    private Long conversationId;
    private Long userId;
    private String reactionType;
    private boolean added;
}
