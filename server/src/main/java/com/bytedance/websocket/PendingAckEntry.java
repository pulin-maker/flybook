package com.bytedance.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待确认的推送消息条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingAckEntry {

    private Long messageId;
    private Long userId;
    private int retryCount;
    private String pushJson;
}
