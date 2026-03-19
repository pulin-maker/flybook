package com.bytedance.modules.message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.modules.message.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
