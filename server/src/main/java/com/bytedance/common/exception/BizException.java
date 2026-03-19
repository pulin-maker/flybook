package com.bytedance.common.exception;

/**
 * 业务异常
 * 所有业务逻辑中的异常都应使用此类，并指定对应的 ErrorCode
 */
public class BizException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String detail;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + (detail != null ? " - " + detail : ""));
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetail() {
        return detail;
    }
}
