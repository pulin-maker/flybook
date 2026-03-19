package com.bytedance.infrastructure.mq;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.infrastructure.mq.MqFailedMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MqFailedMessageMapper extends BaseMapper<MqFailedMessage> {
}
