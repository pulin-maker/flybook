package com.bytedance.modules.conversation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.modules.conversation.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    @Update("UPDATE conversations SET current_seq = #{newSeq}, " +
            "last_msg_content = #{lastMsgContent}, " +
            "last_msg_time = #{lastMsgTime} " +
            "WHERE conversation_id = #{conversationId} AND current_seq < #{newSeq}")
    int casUpdateSeqAndSummary(@Param("conversationId") Long conversationId,
                               @Param("newSeq") Long newSeq,
                               @Param("lastMsgContent") String lastMsgContent,
                               @Param("lastMsgTime") LocalDateTime lastMsgTime);
}
