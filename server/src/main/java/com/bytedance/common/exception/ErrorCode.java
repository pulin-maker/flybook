package com.bytedance.common.exception;

/**
 * 错误码枚举
 * 规范：
 * 10000-19999: 用户相关
 * 20000-29999: 会话相关
 * 30000-39999: 消息相关
 * 40000-49999: 权限/业务相关
 * 50000+: 系统错误
 */
public enum ErrorCode {
    // 用户相关
    USER_NOT_FOUND(10001, "用户不存在"),
    PASSWORD_WRONG(10002, "密码错误"),
    USER_ID_REQUIRED(10003, "无法获取用户ID，请提供 userId"),
    USERNAME_ALREADY_EXISTS(10004, "用户名已存在"),

    // 会话相关
    CONVERSATION_NOT_FOUND(20001, "会话不存在"),
    NOT_CONVERSATION_MEMBER(20002, "您不是该会话成员，无法发送消息"),
    DM_MEMBER_LIMIT(20003, "单聊只能有2个成员"),

    // 消息相关
    MESSAGE_NOT_FOUND(30001, "消息不存在"),
    CONTENT_EMPTY(30002, "消息内容不能为空"),
    MESSAGE_REVOKE_TIMEOUT(30003, "撤回超时（仅支持 2 分钟内的消息）"),
    MESSAGE_REVOKE_NO_PERMISSION(30004, "无权撤回此消息"),
    MESSAGE_REVOKED(30005, "消息已撤回"),
    MESSAGE_EDIT_TIMEOUT(30006, "编辑超时（仅支持 24 小时内的消息）"),
    MESSAGE_EDIT_NO_PERMISSION(30007, "无权编辑此消息"),

    // 权限/业务
    PERMISSION_DENIED(40001, "权限不足"),
    RATE_LIMIT_EXCEEDED(40002, "请求过于频繁"),
    PARAM_INVALID(40003, "参数校验失败"),
    USER_IS_MUTED(40004, "您已被禁言，无法发送消息"),
    CONCURRENT_OPERATION(40005, "操作过于频繁，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
