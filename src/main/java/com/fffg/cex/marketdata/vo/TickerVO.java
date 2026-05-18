package com.fffg.cex.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 24 小时行情概要 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TickerVO {
    private String symbol;
    private BigDecimal lastPrice;            // 最新成交价
    private BigDecimal openPrice;            // 24小时前开盘价
    private BigDecimal highPrice;            // 24小时最高价
    private BigDecimal lowPrice;             // 24小时最低价
    private BigDecimal volume;               // 24小时成交量（基础币数量）
    private BigDecimal amount;               // 24小时成交额（计价币数量）
    private BigDecimal priceChange;          // 价格变化 = lastPrice - openPrice
    private BigDecimal priceChangePercent;   // 涨跌幅百分比，保留两位小数

    /**
     * 返回一个全为 0 的空 ticker（用于系统刚启动无数据时）
     */
    public static TickerVO empty(String symbol) {
        return new TickerVO(symbol, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
