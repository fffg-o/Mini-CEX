package com.fffg.cex.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * K 线（蜡烛图）VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlineVO {
    private LocalDateTime openTime;     // K 线开盘时间
    private BigDecimal openPrice;       // 开盘价
    private BigDecimal highPrice;       // 最高价
    private BigDecimal lowPrice;        // 最低价
    private BigDecimal closePrice;      // 收盘价
    private BigDecimal volume;          // 成交量（基础币）
    private BigDecimal amount;          // 成交额（计价币）
}
