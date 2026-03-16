package com.bytedance.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发送事件 DTO
 * 用于 RocketMQ 异步投递消息推送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSentEvent {

    private Long messageId;
    private Long conversationId;
    private Long senderId;
    private Long seq;
    private Integer msgType;
    private String content;
    private String createdTime;
}
