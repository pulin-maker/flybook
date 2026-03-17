package com.bytedance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRevokedEvent {
    private Long messageId;
    private Long conversationId;
    private Long operatorId;
    private String revokedTime;
}
