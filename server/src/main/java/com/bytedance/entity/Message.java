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
@TableName("messages")
public class Message {
    @TableId(type = IdType.ASSIGN_ID)
    private Long messageId;

    private Long conversationId;

    private Long senderId;

    // 会话内序列号
    private Long seq;

    private Long quoteId;

    //1=Text, 2=Image, 3=Video, 4=File, 5=TodoCard
    private Integer msgType;

    // 先存 JSON 字符串，后面再用 Hutool 转JSON
    private String content;

    // 存 JSON 字符串  被 @ 的人
    private String mentions;

    // 撤回 0=否, 1=是
    private Integer isRevoked;

    // 是否已编辑 0=否, 1=是
    private Integer isEdited;

    // 编辑后的内容
    private String editedContent;

    // 最后编辑时间
    private LocalDateTime editTime;

    private LocalDateTime createdTime;
}
