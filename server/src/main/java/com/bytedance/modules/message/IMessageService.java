package com.bytedance.modules.message;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bytedance.modules.message.Message;
import com.bytedance.modules.message.MessageReaction;

import java.util.List;

public interface IMessageService extends IService<Message> {
    Message sendTextMsg(Long conversationId, Long senderId, String text);
    Message sendMessage(Long conversationId, Long senderId, Integer msgType, String contentJson,
                        List<Long> mentionUserIds, Long quoteId);
    List<Message> syncMessages(Long conversationId, Long afterSeq);

    /** 消息撤回 */
    void revokeMessage(Long messageId, Long operatorId);

    /** 消息编辑 */
    Message editMessage(Long messageId, Long operatorId, String newContent);

    /** 切换表情回应 */
    boolean toggleReaction(Long messageId, Long userId, String reactionType);

    /** 获取消息的表情回应列表 */
    List<MessageReaction> getReactions(Long conversationId, Long messageId);
}
