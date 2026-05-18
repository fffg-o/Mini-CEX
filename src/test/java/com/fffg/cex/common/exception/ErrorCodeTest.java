package com.fffg.cex.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void testSuccessCode() {
        assertEquals(0, ErrorCode.SUCCESS.getCode());
        assertEquals("success", ErrorCode.SUCCESS.getMessage());
    }

    @Test
    void testAccountErrorCodes() {
        assertEquals(40001, ErrorCode.ACCOUNT_NOT_FOUND.getCode());
        assertEquals("账户不存在", ErrorCode.ACCOUNT_NOT_FOUND.getMessage());

        assertEquals(40002, ErrorCode.ASSET_NOT_FOUND.getCode());
        assertEquals("币种不存在", ErrorCode.ASSET_NOT_FOUND.getMessage());

        assertEquals(40007, ErrorCode.USERNAME_EXISTS.getCode());
        assertEquals("用户名已存在", ErrorCode.USERNAME_EXISTS.getMessage());
    }

    @Test
    void testOrderErrorCodes() {
        assertEquals(40008, ErrorCode.ORDER_NOT_FOUND.getCode());
        assertEquals("订单不存在", ErrorCode.ORDER_NOT_FOUND.getMessage());

        assertEquals(40011, ErrorCode.ORDER_FULLY_FILLED.getCode());
        assertEquals("订单已完全成交，无法撤销", ErrorCode.ORDER_FULLY_FILLED.getMessage());

        assertEquals(40012, ErrorCode.ORDER_ALREADY_CANCELED.getCode());
        assertEquals("订单已撤销", ErrorCode.ORDER_ALREADY_CANCELED.getMessage());
    }

    @Test
    void testMatchingErrorCodes() {
        assertEquals(40013, ErrorCode.PRICE_SCALE_INVALID.getCode());
        assertEquals("价格小数位数超出交易对限制", ErrorCode.PRICE_SCALE_INVALID.getMessage());

        assertEquals(40015, ErrorCode.ORDER_AMOUNT_TOO_SMALL.getCode());
        assertEquals("订单金额小于最小交易金额", ErrorCode.ORDER_AMOUNT_TOO_SMALL.getMessage());
    }

    @Test
    void testWalletErrorCodes() {
        assertEquals(40016, ErrorCode.WITHDRAW_NOT_FOUND.getCode());
        assertEquals("提现记录不存在", ErrorCode.WITHDRAW_NOT_FOUND.getMessage());
    }

    @Test
    void testSystemErrorCode() {
        assertEquals(50000, ErrorCode.SYSTEM_ERROR.getCode());
        assertEquals("系统异常", ErrorCode.SYSTEM_ERROR.getMessage());
    }

    @Test
    void testAllErrorCodesUnique() {
        ErrorCode[] values = ErrorCode.values();
        long distinctCodes = java.util.Arrays.stream(values)
                .map(ErrorCode::getCode)
                .distinct()
                .count();
        assertEquals(values.length, distinctCodes, "所有 ErrorCode 的 code 值必须唯一");
    }
}
