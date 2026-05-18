package com.fffg.cex.common.exception;

import com.fffg.cex.common.result.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        if (e.getCode() >= 40100 && e.getCode() < 40200) {
            log.warn("认证异常: code={}, message={}", e.getCode(), e.getMessage());
        }
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() == null
                ? ErrorCode.PARAM_ERROR.getMessage()
                : e.getBindingResult().getFieldError().getDefaultMessage();
        return ApiResponse.fail(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求参数格式错误", e);
        return ApiResponse.fail(ErrorCode.PARAM_ERROR.getCode(), "请求参数格式错误");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("数据库唯一约束冲突", e);
        String message = e.getMessage();
        if (message != null && message.contains("username")) {
            return ApiResponse.fail(ErrorCode.USERNAME_EXISTS.getCode(), ErrorCode.USERNAME_EXISTS.getMessage());
        }
        return ApiResponse.fail(ErrorCode.DUPLICATE_REQUEST.getCode(), ErrorCode.DUPLICATE_REQUEST.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
