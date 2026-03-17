package com.bytedance.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class SendMsgRequest {
    @NotNull(message = "conversationId 不能为空")
    private Long conversationId;
    private Integer msgType; // 允许前端指定类型 (1=文本, 2=图片, 5=待办事项)
    private String content;  // JSON 字符串格式的内容
    private String text;     // 文本消息内容（如果提供，会自动转换为 content）
    private Long userId;     // 可选的用户ID（当关闭登录系统时使用）
    private List<Long> mentionUserIds; // @提及的用户ID列表
    private Long quoteId;    // 回复的消息ID
}
