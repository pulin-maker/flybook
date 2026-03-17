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
@TableName("mq_failed_messages")
public class MqFailedMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;

    private String exchange;

    private String body;

    private String failReason;

    private Integer resolved;

    private LocalDateTime createTime;
}
