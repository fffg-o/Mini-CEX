package com.fffg.cex.marketdata.vo;

import lombok.Data;

import java.util.List;

/**
 * 订单簿深度 VO
 * <p>
 * bids 和 asks 使用 List<String[]> 格式（与 Binance REST API 一致），
 * 每项为 [price, quantity] 字符串数组，前端解析更方便。
 */
@Data
public class MarketDepthVO {
    private String symbol;
    private List<String[]> bids;    // 买盘，价格从高到低
    private List<String[]> asks;    // 卖盘，价格从低到高
    private Long timestamp;
}
