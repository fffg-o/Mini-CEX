package com.fffg.cex.trade.service;

import com.fffg.cex.trade.vo.TradeVO;

import java.util.List;

/**
 * 成交记录服务接口
 */
public interface TradeService {

    /**
     * 查询某个交易对最近的成交记录
     *
     * @param symbol 交易对
     * @param limit  返回数量，默认 50，最大 200
     * @return 成交记录列表
     */
    List<TradeVO> getRecentTrades(String symbol, int limit);
}
