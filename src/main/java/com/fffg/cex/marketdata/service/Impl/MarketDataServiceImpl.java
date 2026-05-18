package com.fffg.cex.marketdata.service.Impl;

import com.fffg.cex.common.exception.BusinessException;
import com.fffg.cex.common.exception.ErrorCode;
import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.VO.SymbolPairVO;
import com.fffg.cex.marketdata.mapper.MarketDataMapper;
import com.fffg.cex.marketdata.service.MarketDataService;
import com.fffg.cex.marketdata.vo.KlineVO;
import com.fffg.cex.marketdata.vo.MarketDepthVO;
import com.fffg.cex.marketdata.vo.MarketTradeVO;
import com.fffg.cex.marketdata.vo.TickerVO;
import com.fffg.cex.matching.OrderBook;
import com.fffg.cex.matching.OrderBookManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 行情数据服务实现
 * <p>
 * 订单簿 depth 优先从内存 OrderBook 读取（第二版），
 * trades 和 klines 从数据库 trade_fill 表查询。
 */
@Slf4j
@Service
public class MarketDataServiceImpl implements MarketDataService {

    @Autowired
    private MarketDataMapper marketDataMapper;

    @Autowired
    private SymbolPairMapper symbolPairMapper;

    @Autowired
    private OrderBookManager orderBookManager;

    /** 默认返回数量 */
    private static final int DEFAULT_LIMIT = 50;

    /** 最大返回数量 */
    private static final int MAX_LIMIT = 200;

    /** 默认 depth 档位 */
    private static final int DEFAULT_DEPTH_LIMIT = 20;

    /** 最大 depth 档位 */
    private static final int MAX_DEPTH_LIMIT = 100;

    @Override
    public MarketDepthVO getDepth(String symbol, int limit) {
        // 1. 校验交易对
        validateSymbol(symbol);

        // 2. 限制档位
        if (limit <= 0) {
            limit = DEFAULT_DEPTH_LIMIT;
        }
        if (limit > MAX_DEPTH_LIMIT) {
            limit = MAX_DEPTH_LIMIT;
        }

        String symbolUpper = symbol.toUpperCase();

        // 3. 从内存 OrderBook 读取快照
        OrderBook orderBook = orderBookManager.getOrderBook(symbolUpper);
        List<String[]> bids = orderBook.getBidsSnapshot(limit);
        List<String[]> asks = orderBook.getAsksSnapshot(limit);

        // 4. 组装结果
        MarketDepthVO vo = new MarketDepthVO();
        vo.setSymbol(symbolUpper);
        vo.setBids(bids);
        vo.setAsks(asks);
        vo.setTimestamp(System.currentTimeMillis());
        return vo;
    }

    @Override
    public List<MarketTradeVO> getRecentTrades(String symbol, int limit) {
        // 1. 校验交易对
        validateSymbol(symbol);

        // 2. 限制查询数量
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }

        // 3. 查询最新成交
        return marketDataMapper.selectRecentTrades(symbol.toUpperCase(), limit);
    }

    @Override
    public TickerVO getTicker(String symbol) {
        validateSymbol(symbol);

        String symbolUpper = symbol.toUpperCase();

        // 1. 查询 24 小时聚合数据
        TickerVO ticker = marketDataMapper.selectTicker(symbolUpper);

        // 2. 如果无数据，尝试取最新一条成交价
        if (ticker == null || ticker.getLastPrice() == null) {
            BigDecimal lastPrice = marketDataMapper.selectLastPrice(symbolUpper);
            if (lastPrice == null) {
                return TickerVO.empty(symbolUpper);
            }
            // 只有最新价，无 24h 数据
            return new TickerVO(symbolUpper, lastPrice, lastPrice, lastPrice,
                    lastPrice, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // 3. 补充计算字段
        BigDecimal priceChange = ticker.getLastPrice().subtract(ticker.getOpenPrice());
        BigDecimal priceChangePercent = BigDecimal.ZERO;
        if (ticker.getOpenPrice().compareTo(BigDecimal.ZERO) > 0) {
            priceChangePercent = priceChange
                    .multiply(new BigDecimal("100"))
                    .divide(ticker.getOpenPrice(), 2, java.math.RoundingMode.HALF_UP);
        }

        ticker.setSymbol(symbolUpper);
        ticker.setPriceChange(priceChange);
        ticker.setPriceChangePercent(priceChangePercent);
        return ticker;
    }

    @Override
    public List<KlineVO> getKlines(String symbol, String interval, int limit) {
        validateSymbol(symbol);

        if (limit <= 0) {
            limit = 100;
        }
        if (limit > 500) {
            limit = 500;
        }

        // 1. 计算时间窗口秒数
        long windowSeconds = getWindowSeconds(interval);
        if (windowSeconds <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "不支持的 K 线周期: " + interval);
        }

        // 2. 计算起始时间（按 limit 反推）
        LocalDateTime since = LocalDateTime.now().minusSeconds(windowSeconds * limit);
        String sinceStr = since.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 3. 查询聚合数据
        return marketDataMapper.selectKlinesByWindow(symbol.toUpperCase(), windowSeconds, sinceStr, limit);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 校验交易对是否存在且启用
     */
    private void validateSymbol(String symbol) {
        SymbolPairVO pair = symbolPairMapper.selectBySymbol(symbol.toUpperCase());
        if (pair == null) {
            throw new BusinessException(ErrorCode.SYMBOL_NOT_FOUND.getCode(),
                    ErrorCode.SYMBOL_NOT_FOUND.getMessage());
        }
    }

    /**
     * 根据 interval 获取时间窗口秒数
     */
    private long getWindowSeconds(String interval) {
        switch (interval) {
            case "1m":  return 60;
            case "5m":  return 300;
            case "15m": return 900;
            case "30m": return 1800;
            case "1h":  return 3600;
            case "4h":  return 14400;
            case "1d":  return 86400;
            case "1w":  return 604800;
            default:    return -1;
        }
    }
}
