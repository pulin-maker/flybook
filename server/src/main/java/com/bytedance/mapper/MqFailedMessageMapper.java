package com.bytedance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.entity.MqFailedMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MqFailedMessageMapper extends BaseMapper<MqFailedMessage> {
}
