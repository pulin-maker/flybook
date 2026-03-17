package com.bytedance.entity;

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
@TableName("message_reactions")
public class MessageReaction {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long conversationId;

    private Long messageId;

    private Long userId;

    private String reactionType;

    private LocalDateTime createdTime;
}
