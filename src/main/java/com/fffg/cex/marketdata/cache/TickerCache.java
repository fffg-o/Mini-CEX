package com.fffg.cex.marketdata.cache;

import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.VO.SymbolPairVO;
import com.fffg.cex.marketdata.service.MarketDataService;
import com.fffg.cex.marketdata.vo.TickerVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Ticker 缓存，每 10 秒刷新一次，减少数据库查询频率。
 */
@Slf4j
@Component
public class TickerCache {

    private final ConcurrentHashMap<String, TickerVO> cache = new ConcurrentHashMap<>();

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private SymbolPairMapper symbolPairMapper;

    /**
     * 初始化时加载一次
     */
    @PostConstruct
    public void init() {
        refreshTickers();
    }

    /**
     * 每 10 秒刷新所有交易对的 ticker
     */
    @Scheduled(fixedRate = 10000)
    public void refreshTickers() {
        try {
            List<String> symbols = symbolPairMapper.selectEnabledSymbols();
            for (String symbol : symbols) {
                try {
                    TickerVO ticker = marketDataService.getTicker(symbol);
                    cache.put(symbol, ticker);
                } catch (Exception e) {
                    log.warn("刷新 ticker 失败: symbol={}, error={}", symbol, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("刷新 ticker 缓存异常", e);
        }
    }

    /**
     * 获取指定交易对的 ticker
     */
    public TickerVO getTicker(String symbol) {
        return cache.getOrDefault(symbol.toUpperCase(), TickerVO.empty(symbol));
    }

    /**
     * 获取所有交易对的 ticker
     */
    public List<TickerVO> getAllTickers() {
        return cache.values().stream().collect(Collectors.toList());
    }
}
