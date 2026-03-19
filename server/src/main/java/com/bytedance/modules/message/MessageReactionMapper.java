package com.bytedance.modules.message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.modules.message.MessageReaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageReactionMapper extends BaseMapper<MessageReaction> {
}
