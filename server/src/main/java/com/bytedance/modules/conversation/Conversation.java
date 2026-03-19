package com.bytedance.modules.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversations")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long conversationId;

    // 1=单聊, 2=群聊
    private Integer type;

    private String name;

    private String avatarUrl;

    private Long ownerId;

    // 当前序列号 (默认0)
    @Builder.Default
    private Long currentSeq = 0L;

    private String lastMsgContent;

    private LocalDateTime lastMsgTime;

    private LocalDateTime createdTime;
}
