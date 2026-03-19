package com.bytedance.modules.conversation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.modules.conversation.ConversationMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {

    // 原子更新未读数
    // 使用 MyBatis 的注解写原生 SQL，性能最高
    // 逻辑：给群里除了发送者(senderId)以外的所有人，unread_count + 1
    @Update("UPDATE conversation_members SET unread_count = unread_count + 1 " +
            "WHERE conversation_id = #{conversationId} AND user_id != #{senderId}")
    void incrementUnreadCount(@Param("conversationId") Long conversationId,
                              @Param("senderId") Long senderId);

    // 清除某个会话的未读消息数
    @Update("UPDATE conversation_members SET unread_count = 0 " +
            "WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    void clearUnreadCount(@Param("conversationId") Long conversationId,
                          @Param("userId") Long userId);

    // 清除用户所有会话的未读消息数
    @Update("UPDATE conversation_members SET unread_count = 0 " +
            "WHERE user_id = #{userId}")
    void clearAllUnreadCount(@Param("userId") Long userId);
}
