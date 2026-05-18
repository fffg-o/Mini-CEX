package com.fffg.cex.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 成功
    SUCCESS(0, "success"),

    // 账户相关 40001-40010
    ACCOUNT_NOT_FOUND(40001, "账户不存在"),
    ASSET_NOT_FOUND(40002, "币种不存在"),
    SYMBOL_NOT_FOUND(40003, "交易对不存在"),
    INSUFFICIENT_BALANCE(40004, "余额不足"),
    INVALID_ORDER_STATUS(40005, "订单状态非法"),
    PARAM_ERROR(40006, "参数错误"),
    USERNAME_EXISTS(40007, "用户名已存在"),
    ORDER_NOT_FOUND(40008, "订单不存在"),
    ASSET_BALANCE_NOT_FOUND(40009, "资产余额不存在"),
    DUPLICATE_REQUEST(40010, "重复请求"),

    // 撮合相关 40011-40015
    ORDER_FULLY_FILLED(40011, "订单已完全成交，无法撤销"),
    ORDER_ALREADY_CANCELED(40012, "订单已撤销"),
    PRICE_SCALE_INVALID(40013, "价格小数位数超出交易对限制"),
    QUANTITY_SCALE_INVALID(40014, "数量小数位数超出交易对限制"),
    ORDER_AMOUNT_TOO_SMALL(40015, "订单金额小于最小交易金额"),

    // 钱包相关 40016-40020
    WITHDRAW_NOT_FOUND(40016, "提现记录不存在"),
    INVALID_WITHDRAW_STATUS(40017, "提现状态非法"),
    DEPOSIT_NOT_FOUND(40018, "充值记录不存在"),

    // 系统异常
    SYSTEM_ERROR(50000, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
