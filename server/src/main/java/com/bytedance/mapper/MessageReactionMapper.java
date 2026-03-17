package com.bytedance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.entity.MessageReaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageReactionMapper extends BaseMapper<MessageReaction> {
}
