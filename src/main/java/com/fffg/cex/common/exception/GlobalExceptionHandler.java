package com.fffg.cex.common.exception;

import com.fffg.cex.common.result.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e){
        return ApiResponse.fail(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e){
        String  message  = e.getBindingResult().getFieldError() == null
                ? ErrorCode.PARAM_ERROR.getMessage()
                : e.getBindingResult().getFieldError().getDefaultMessage();

        return ApiResponse.fail(ErrorCode.PARAM_ERROR.getCode() , message);

    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e){
        log.error("系统异常",e);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR.getCode(),ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
