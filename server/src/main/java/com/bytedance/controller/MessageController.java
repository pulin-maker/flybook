package com.bytedance.controller;

import cn.hutool.json.JSONUtil;
import com.bytedance.common.Result;
import com.bytedance.dto.SendMsgRequest;
import com.bytedance.dto.request.EditMsgRequest;
import com.bytedance.dto.request.ReactionRequest;
import com.bytedance.dto.request.RevokeMsgRequest;
import com.bytedance.entity.Message;
import com.bytedance.entity.MessageReaction;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import com.bytedance.ratelimit.RateLimit;
import com.bytedance.service.IMessageService;
import com.bytedance.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private IMessageService messageService;

    /**
     * 发送消息
     */
    @PostMapping("/send")
    @RateLimit(permits = 30, window = 60)
    public Result<Message> send(@Valid @RequestBody SendMsgRequest request) {
        Long currentUserId = getUserId(request.getUserId());
        if (request.getMsgType() == null) request.setMsgType(1);

        String contentJson;
        if (StringUtils.hasLength(request.getText())) {
            contentJson = JSONUtil.createObj().set("text", request.getText()).toString();
        } else if (StringUtils.hasLength(request.getContent())) {
            contentJson = request.getContent();
        } else {
            throw new BizException(ErrorCode.CONTENT_EMPTY);
        }

        Message msg = messageService.sendMessage(
                request.getConversationId(),
                currentUserId,
                request.getMsgType(),
                contentJson,
                request.getMentionUserIds(),
                request.getQuoteId()
        );
        return Result.success(msg);
    }

    /**
     * 同步/拉取消息
     */
    @GetMapping("/sync")
    public Result<List<Message>> syncMessages(
            @RequestParam Long conversationId,
            @RequestParam(defaultValue = "0") Long afterSeq
    ) {
        List<Message> messages = messageService.syncMessages(conversationId, afterSeq);
        return Result.success(messages);
    }

    /**
     * 根据消息ID获取单条消息
     */
    @GetMapping("/{messageId}")
    public Result<Message> getById(@PathVariable Long messageId) {
        Message msg = messageService.getById(messageId);
        return Result.success(msg);
    }

    /**
     * 消息撤回
     */
    @PostMapping("/revoke")
    public Result<Void> revoke(@Valid @RequestBody RevokeMsgRequest request) {
        Long userId = getUserId(null);
        messageService.revokeMessage(request.getMessageId(), userId);
        return Result.success();
    }

    /**
     * 消息编辑
     */
    @PostMapping("/edit")
    public Result<Message> edit(@Valid @RequestBody EditMsgRequest request) {
        Long userId = getUserId(null);
        Message msg = messageService.editMessage(request.getMessageId(), userId, request.getNewContent());
        return Result.success(msg);
    }

    /**
     * 切换表情回应
     */
    @PostMapping("/reactions/toggle")
    public Result<Boolean> toggleReaction(@Valid @RequestBody ReactionRequest request) {
        Long userId = getUserId(null);
        boolean added = messageService.toggleReaction(request.getMessageId(), userId, request.getReactionType());
        return Result.success(added);
    }

    /**
     * 获取消息的表情回应列表
     */
    @GetMapping("/reactions")
    public Result<List<MessageReaction>> getReactions(
            @RequestParam Long conversationId,
            @RequestParam Long messageId) {
        List<MessageReaction> reactions = messageService.getReactions(conversationId, messageId);
        return Result.success(reactions);
    }

    private Long getUserId(Long requestBodyUserId) {
        if (requestBodyUserId != null) {
            return requestBodyUserId;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.USER_ID_REQUIRED);
        }
        return userId;
    }
}

