package com.fffg.cex.common.exception;

import com.fffg.cex.common.result.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleBusinessException() {
        BusinessException ex = new BusinessException(40001, "账户不存在");
        ApiResponse<Void> response = handler.handleBusinessException(ex);
        assertEquals(40001, response.getCode());
        assertEquals("账户不存在", response.getMessage());
    }

    @Test
    void testHandleValidationException() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "object");
        bindingResult.addError(new FieldError("object", "username", "用户名不能为空"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ApiResponse<Void> response = handler.handleValidationException(ex);
        assertEquals(40006, response.getCode());
        assertEquals("用户名不能为空", response.getMessage());
    }

    @Test
    void testHandleValidationExceptionNoFieldError() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "object");
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ApiResponse<Void> response = handler.handleValidationException(ex);
        assertEquals(40006, response.getCode());
        assertEquals("参数错误", response.getMessage());
    }

    @Test
    void testHandleHttpMessageNotReadableException() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "json parse error", null, null);
        ApiResponse<Void> response = handler.handleHttpMessageNotReadableException(ex);
        assertEquals(40006, response.getCode());
        assertEquals("请求参数格式错误", response.getMessage());
    }

    @Test
    void testHandleDuplicateKeyExceptionWithUsername() {
        DuplicateKeyException ex = new DuplicateKeyException("Duplicate entry 'test' for key 'username'");
        ApiResponse<Void> response = handler.handleDuplicateKeyException(ex);
        assertEquals(40007, response.getCode());
        assertEquals("用户名已存在", response.getMessage());
    }

    @Test
    void testHandleDuplicateKeyExceptionGeneric() {
        DuplicateKeyException ex = new DuplicateKeyException("Duplicate entry for key 'PRIMARY'");
        ApiResponse<Void> response = handler.handleDuplicateKeyException(ex);
        assertEquals(40010, response.getCode());
        assertEquals("重复请求", response.getMessage());
    }

    @Test
    void testHandleDuplicateKeyExceptionNullMessage() {
        DuplicateKeyException ex = new DuplicateKeyException((String) null);
        ApiResponse<Void> response = handler.handleDuplicateKeyException(ex);
        assertEquals(40010, response.getCode());
        assertEquals("重复请求", response.getMessage());
    }

    @Test
    void testHandleGenericException() {
        Exception ex = new RuntimeException("未知错误");
        ApiResponse<Void> response = handler.handleException(ex);
        assertEquals(50000, response.getCode());
        assertEquals("系统异常", response.getMessage());
    }
}
