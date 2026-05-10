package com.fffg.cex.matching;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 撮合结果，记录一次成交的详细信息
 */
@Data
public class MatchResult {
    /** 交易对 */
    private String symbol;
    /** 交易编号 */
    private String tradeNo;
    /** 成交价格（= maker 价格） */
    private BigDecimal price;
    /** 成交数量 */
    private BigDecimal quantity;
    /** 成交金额 = price * quantity */
    private BigDecimal amount;

    /** 买单 ID */
    private Long buyOrderId;
    /** 卖单 ID */
    private Long sellOrderId;
    /** 买方账户 ID */
    private Long buyAccountId;
    /** 卖方账户 ID */
    private Long sellAccountId;

    /** 买方订单号 */
    private String buyOrderNo;
    /** 卖方订单号 */
    private String sellOrderNo;

    /** 买方手续费（USDT） */
    private BigDecimal buyFee;
    /** 卖方手续费（BTC） */
    private BigDecimal sellFee;

    /** 买方冻结金额（price * quantity 中 USDT 部分） */
    private BigDecimal buyFrozenAmount;
    /** 买方实际成交金额（成交价 * quantity） */
    private BigDecimal buyActualAmount;
    /** 买方价差退款 */
    private BigDecimal buyRefundAmount;

    /** 卖方冻结数量 */
    private BigDecimal sellFrozenQuantity;
    /** 卖方实际成交数量 */
    private BigDecimal sellActualQuantity;
}
