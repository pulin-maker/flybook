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
@TableName("conversation_members")
public class ConversationMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long userId;

    // 读扩散索引
    @Builder.Default
    private Long lastAckSeq = 0L;

    // 未读数
    @Builder.Default
    private Integer unreadCount = 0;

    private Integer role;
    private Integer isMuted;

    private Boolean isTop;


    private LocalDateTime joinedTime;
}

