package com.fffg.cex.common.exception;

import lombok.Getter;

@Getter

public enum ErrorCode {

    ACCOUNT_NOT_FOUND(40001, "账户不存在"),
    ASSET_NOT_FOUND(40002, "币种不存在"),
    SYMBOL_NOT_FOUND(40003, "交易对不存在"),
    INSUFFICIENT_BALANCE(40004, "余额不足"),
    INVALID_ORDER_STATUS(40005, "订单状态非法"),
    PARAM_ERROR(40006, "参数错误"),
    SYSTEM_ERROR(50000, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
