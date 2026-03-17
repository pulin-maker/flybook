package com.bytedance.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

/**
 * 统一返回结果
 * code: 0 成功，> 0 失败（具体错误码见 ErrorCode 枚举）
 * msg: 详细错误信息
 * data: 响应数据
 * traceId: 链路追踪ID
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;
    private String traceId;

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data, MDC.get("traceId"));
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "success", null, MDC.get("traceId"));
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(1, msg, null, MDC.get("traceId"));
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null, MDC.get("traceId"));
    }
}
