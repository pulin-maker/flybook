package com.bytedance.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bytedance.entity.Message;
import com.bytedance.entity.MessageReaction;
import com.bytedance.mapper.MessageMapper;
import com.bytedance.repository.IMessageReactionRepository;
import com.bytedance.service.IMessageService;
import com.bytedance.usecase.message.EditMessageUseCase;
import com.bytedance.usecase.message.RevokeMessageUseCase;
import com.bytedance.usecase.message.SendMessageUseCase;
import com.bytedance.usecase.message.SyncMessagesUseCase;
import com.bytedance.usecase.message.ToggleReactionUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements IMessageService {

    private final SendMessageUseCase sendMessageUseCase;
    private final SyncMessagesUseCase syncMessagesUseCase;
    private final RevokeMessageUseCase revokeMessageUseCase;
    private final EditMessageUseCase editMessageUseCase;
    private final ToggleReactionUseCase toggleReactionUseCase;
    private final IMessageReactionRepository reactionRepository;

    @Autowired
    public MessageServiceImpl(SendMessageUseCase sendMessageUseCase,
                             SyncMessagesUseCase syncMessagesUseCase,
                             RevokeMessageUseCase revokeMessageUseCase,
                             EditMessageUseCase editMessageUseCase,
                             ToggleReactionUseCase toggleReactionUseCase,
                             IMessageReactionRepository reactionRepository) {
        this.sendMessageUseCase = sendMessageUseCase;
        this.syncMessagesUseCase = syncMessagesUseCase;
        this.revokeMessageUseCase = revokeMessageUseCase;
        this.editMessageUseCase = editMessageUseCase;
        this.toggleReactionUseCase = toggleReactionUseCase;
        this.reactionRepository = reactionRepository;
    }

    @Override
    public Message sendMessage(Long conversationId, Long senderId, Integer msgType, String contentJson,
                               List<Long> mentionUserIds, Long quoteId) {
        return sendMessageUseCase.execute(conversationId, senderId, msgType, contentJson, mentionUserIds, quoteId);
    }

    @Override
    public Message sendTextMsg(Long conversationId, Long senderId, String text) {
        String json = JSONUtil.createObj().set("text", text).toString();
        return sendMessage(conversationId, senderId, 1, json, null, null);
    }

    @Override
    public List<Message> syncMessages(Long conversationId, Long afterSeq) {
        return syncMessagesUseCase.execute(conversationId, afterSeq);
    }

    @Override
    public void revokeMessage(Long messageId, Long operatorId) {
        revokeMessageUseCase.execute(messageId, operatorId);
    }

    @Override
    public Message editMessage(Long messageId, Long operatorId, String newContent) {
        return editMessageUseCase.execute(messageId, operatorId, newContent);
    }

    @Override
    public boolean toggleReaction(Long messageId, Long userId, String reactionType) {
        return toggleReactionUseCase.execute(messageId, userId, reactionType);
    }

    @Override
    public List<MessageReaction> getReactions(Long conversationId, Long messageId) {
        return reactionRepository.findByMessageId(conversationId, messageId);
    }
}

