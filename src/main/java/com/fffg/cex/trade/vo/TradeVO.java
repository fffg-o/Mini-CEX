package com.fffg.cex.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成交记录 VO
 */
@Data
public class TradeVO {
    private Long tradeId;
    private String tradeNo;
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal amount;
    private Long buyOrderId;
    private Long sellOrderId;
    private Long buyAccountId;
    private Long sellAccountId;
    private BigDecimal buyFee;
    private BigDecimal sellFee;
    private LocalDateTime createdAt;
}
