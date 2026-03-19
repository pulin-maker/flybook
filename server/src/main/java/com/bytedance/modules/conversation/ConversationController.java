package com.bytedance.modules.conversation;

import cn.hutool.log.Log;
import com.bytedance.modules.conversation.AddMemberRequest;
import com.bytedance.modules.conversation.ConversationDTO;
import com.bytedance.modules.conversation.TopRequest;
import com.bytedance.modules.conversation.KickMemberRequest;
import com.bytedance.modules.conversation.MuteUserRequest;
import com.bytedance.modules.conversation.SetAdminRequest;
import com.bytedance.modules.conversation.TransferOwnerRequest;
import com.bytedance.common.exception.BizException;
import com.bytedance.common.exception.ErrorCode;
import com.bytedance.infrastructure.concurrent.RateLimit;
import com.bytedance.common.utils.UserContext;
import com.bytedance.modules.conversation.ConversationVO;
import com.bytedance.common.Result;
import com.bytedance.modules.conversation.IConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @Autowired
    private IConversationService conversationService;

    /**
     * 获取当前用户的会话列表
     * URL: GET /api/conversations/list?userId=1001
     */
    @GetMapping("/list")
    public Result<List<ConversationVO>> getList() {
        Long userId = getUserId();
        List<ConversationVO> list = conversationService.getConversationList(userId);
        return Result.success(list);
    }

    /**
     * 创建会话 (为了方便 APIFox 测试，也暴露出来)
     * 如果创建会话时提供了成员列表，会先检查是否存在相同会话名和成员的会话
     */
    @PostMapping("/create")
    @RateLimit(permits = 10, window = 60)
    public Result<Long> create(@RequestBody ConversationDTO request) {
        Long userId = getUserId(request.getUserId());
        // 如果请求体中提供了 ownerId，使用它；否则使用当前用户ID
        Long ownerId = request.getOwnerId() != null ? request.getOwnerId() : userId;
        
        // 如果提供了成员列表，先检查是否存在相同的会话
        if (request.getType() != null
                && request.getTargetUserIds() != null && !request.getTargetUserIds().isEmpty()) {
            // 构建完整的成员列表（包括创建者），并去重
            Set<Long> allMemberSet = new java.util.HashSet<>();
            allMemberSet.add(ownerId);
            allMemberSet.addAll(request.getTargetUserIds());
            List<Long> allMemberIds = new java.util.ArrayList<>(allMemberSet);
            
            // 检查是否存在相同的会话
            Long existingConversationId = conversationService.findExistingConversation(
                    request.getName(), request.getType(), allMemberIds);
            
            if (existingConversationId != null) {
                // 如果存在相同的群聊，返回已有群聊ID
                return Result.success(existingConversationId);
            }
        }
        
        // 创建新群聊
        long id = conversationService.createConversation(request.getName(), request.getType(), ownerId);
        
        // 如果提供了成员列表，添加成员
        if (request.getTargetUserIds() != null && !request.getTargetUserIds().isEmpty()) {
            conversationService.addMembers(id, request.getTargetUserIds(), ownerId);
        }
        
        return Result.success(id);
    }

    /**
     * 邀请成员入群
     * URL: POST /api/conversations/members/add
     */
    @PostMapping("/members/add")
    public Result<Void> addMembers(@RequestBody AddMemberRequest request) {
        Long userId = getUserId(request.getInviterId());
        conversationService.addMembers(
                request.getConversationId(),
                request.getTargetUserIds(),
                userId
        );
        return Result.success();
    }

    /**
     * 清除某个会话的未读消息数
     * URL: POST /api/conversations/unread/clear?conversationId=1
     */
    @PostMapping("/unread/clear")
    public Result<Void> clearUnreadCount(@RequestParam Long conversationId) {
        Long userId = getUserId();
        conversationService.clearUnreadCount(conversationId, userId);
        return Result.success();
    }

    /**
     * 清除用户所有会话的未读消息数
     * URL: POST /api/conversations/unread/clear-all
     */
    @PostMapping("/unread/clear-all")
    public Result<Void> clearAllUnreadCount() {
        Long userId = getUserId();
        conversationService.clearAllUnreadCount(userId);
        return Result.success();
    }

    /**
     * 获取用户ID，优先使用请求体中的 userId，否则使用 UserContext 中的
     */
    private Long getUserId() {
        return getUserId(null);
    }

    /**
     * 获取用户ID，优先级：请求体中的 userId > UserContext 中的 userId
     */
    private Long getUserId(Long requestBodyUserId) {
        // 如果请求体中提供了 userId，优先使用
        if (requestBodyUserId != null) {
            return requestBodyUserId;
        }
        // 否则使用 UserContext 中的 userId（由拦截器设置）
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.USER_ID_REQUIRED);
        }
        return userId;
    }

    @PostMapping("/top")
    public Result<String> setTop(@RequestBody TopRequest request) {
        Long userId = UserContext.getUserId();
        conversationService.setConversationTop(request.getConversationId(), userId, request.getIsTop());
        return Result.success("操作成功");
    }

    // ======== 群组权限管理 ========

    /**
     * 踢出成员
     */
    @PostMapping("/members/kick")
    public Result<Void> kickMember(@Valid @RequestBody KickMemberRequest request) {
        Long userId = getUserId();
        conversationService.kickMember(request.getConversationId(), userId, request.getTargetUserId());
        return Result.success();
    }

    /**
     * 设置/取消管理员
     */
    @PostMapping("/members/set-admin")
    public Result<Void> setAdmin(@Valid @RequestBody SetAdminRequest request) {
        Long userId = getUserId();
        conversationService.setAdmin(request.getConversationId(), userId, request.getTargetUserId(), request.getIsAdmin());
        return Result.success();
    }

    /**
     * 转让群主
     */
    @PostMapping("/transfer-owner")
    public Result<Void> transferOwner(@Valid @RequestBody TransferOwnerRequest request) {
        Long userId = getUserId();
        conversationService.transferOwner(request.getConversationId(), userId, request.getNewOwnerId());
        return Result.success();
    }

    /**
     * 解散群聊
     */
    @PostMapping("/dissolve")
    public Result<Void> dissolveGroup(@RequestParam Long conversationId) {
        Long userId = getUserId();
        conversationService.dissolveGroup(conversationId, userId);
        return Result.success();
    }

    /**
     * 禁言/解除禁言
     */
    @PostMapping("/members/mute")
    public Result<Void> muteUser(@Valid @RequestBody MuteUserRequest request) {
        Long userId = getUserId();
        conversationService.muteUser(request.getConversationId(), userId, request.getTargetUserId(), request.getIsMuted());
        return Result.success();
    }
}

