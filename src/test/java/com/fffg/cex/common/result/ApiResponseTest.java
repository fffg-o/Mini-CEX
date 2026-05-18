package com.fffg.cex.common.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void testSuccessWithData() {
        ApiResponse<String> response = ApiResponse.success("test-data");
        assertEquals(0, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("test-data", response.getData());
        assertNull(response.getTraceId());
    }

    @Test
    void testSuccessWithDataAndTraceId() {
        ApiResponse<String> response = ApiResponse.success("data", "trace-123");
        assertEquals(0, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("data", response.getData());
        assertEquals("trace-123", response.getTraceId());
    }

    @Test
    void testSuccessVoid() {
        ApiResponse<Void> response = ApiResponse.success();
        assertEquals(0, response.getCode());
        assertEquals("success", response.getMessage());
        assertNull(response.getData());
        assertNull(response.getTraceId());
    }

    @Test
    void testFailWithCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.fail(40001, "账户不存在");
        assertEquals(40001, response.getCode());
        assertEquals("账户不存在", response.getMessage());
        assertNull(response.getData());
        assertNull(response.getTraceId());
    }

    @Test
    void testFailWithCodeMessageAndTraceId() {
        ApiResponse<Void> response = ApiResponse.fail(500, "系统异常", "trace-999");
        assertEquals(500, response.getCode());
        assertEquals("系统异常", response.getMessage());
        assertNull(response.getData());
        assertEquals("trace-999", response.getTraceId());
    }

    @Test
    void testFailWithMessageOnly() {
        ApiResponse<Void> response = ApiResponse.fail("发生错误");
        assertEquals(500, response.getCode());
        assertEquals("发生错误", response.getMessage());
        assertNull(response.getData());
        assertNull(response.getTraceId());
    }

    @Test
    void testDataIsNullWhenNotProvided() {
        ApiResponse<String> response = ApiResponse.success(null);
        assertEquals(0, response.getCode());
        assertNull(response.getData());
    }
}
