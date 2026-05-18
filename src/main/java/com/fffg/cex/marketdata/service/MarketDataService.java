package com.fffg.cex.marketdata.service;

import com.fffg.cex.marketdata.vo.KlineVO;
import com.fffg.cex.marketdata.vo.MarketDepthVO;
import com.fffg.cex.marketdata.vo.MarketTradeVO;
import com.fffg.cex.marketdata.vo.TickerVO;

import java.util.List;

/**
 * 行情数据服务接口
 */
public interface MarketDataService {

    /**
     * 查询订单簿深度
     *
     * @param symbol 交易对
     * @param limit  档位数量
     * @return 订单簿深度
     */
    MarketDepthVO getDepth(String symbol, int limit);

    /**
     * 查询最新成交记录（行情精简版）
     *
     * @param symbol 交易对
     * @param limit  返回数量
     * @return 成交记录列表
     */
    List<MarketTradeVO> getRecentTrades(String symbol, int limit);

    /**
     * 获取 24 小时 ticker
     *
     * @param symbol 交易对
     * @return ticker 数据
     */
    TickerVO getTicker(String symbol);

    /**
     * 查询 K 线数据
     *
     * @param symbol   交易对
     * @param interval 周期（1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w）
     * @param limit    返回数量
     * @return K 线列表
     */
    List<KlineVO> getKlines(String symbol, String interval, int limit);
}
