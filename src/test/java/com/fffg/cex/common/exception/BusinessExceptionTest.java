package com.fffg.cex.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void testConstructorWithCodeAndMessage() {
        BusinessException ex = new BusinessException(40001, "账户不存在");
        assertEquals(40001, ex.getCode());
        assertEquals("账户不存在", ex.getMessage());
    }

    @Test
    void testConstructorWithMessageOnly() {
        BusinessException ex = new BusinessException("自定义错误");
        assertEquals(500, ex.getCode());
        assertEquals("自定义错误", ex.getMessage());
    }

    @Test
    void testIsRuntimeException() {
        BusinessException ex = new BusinessException(40007, "用户名已存在");
        assertTrue(ex instanceof RuntimeException);
    }
}
