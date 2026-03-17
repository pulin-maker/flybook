package com.bytedance.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 广播推送消息
 * 通过 Fanout Exchange 广播到所有实例，由持有 Session 的实例执行本地推送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsPushMessage {
    private Long userId;        // 目标用户
    private String pushJson;    // 推送内容 JSON
    private Long messageId;     // 消息ID（用于 ACK 追踪，可为 null）
    private boolean requireAck; // 是否需要 ACK 追踪
}
