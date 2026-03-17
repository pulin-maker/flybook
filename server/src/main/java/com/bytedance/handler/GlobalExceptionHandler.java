package com.bytedance.handler;

import com.bytedance.common.Result;
import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理
 *
 * 异常处理顺序（越具体越先处理）：
 * 1. BizException - 业务异常，返回业务错误码
 * 2. MethodArgumentNotValidException - @Valid 注解验证失败（RequestBody）
 * 3. ConstraintViolationException - @Validated 注解验证失败（Path/Query）
 * 4. Exception - 系统异常，不暴露细节
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常 (BizException)
     * 返回业务错误码和清晰的错误信息
     */
    @ExceptionHandler(BizException.class)
    public <T> Result<T> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getErrorCode().getMessage());
        return Result.fail(e.getErrorCode().getCode(), e.getErrorCode().getMessage());
    }

    /**
     * 捕获 @Valid 参数验证失败（RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public <T> Result<T> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String errors = bindingResult.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("参数校验失败: {}", errors);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "参数校验失败: " + errors);
    }

    /**
     * 捕获 @Validated 参数验证失败（Path/Query）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public <T> Result<T> handleConstraintViolation(ConstraintViolationException e) {
        String errors = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        log.warn("参数校验失败: {}", errors);
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "参数校验失败: " + errors);
    }

    /**
     * 捕获所有其他异常（系统异常）
     * 不暴露具体的异常堆栈信息给客户端，防止信息泄露
     */
    @ExceptionHandler(Exception.class)
    public <T> Result<T> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.fail(50000, "服务器内部错误，请稍后重试");
    }
}


