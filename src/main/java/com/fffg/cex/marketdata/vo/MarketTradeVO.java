package com.fffg.cex.marketdata.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 行情最新成交 VO（精简版，用于行情展示）
 */
@Data
public class MarketTradeVO {
    private Long tradeId;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
