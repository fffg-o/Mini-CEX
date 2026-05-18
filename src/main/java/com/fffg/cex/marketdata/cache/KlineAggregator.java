package com.fffg.cex.marketdata.cache;

import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.VO.SymbolPairVO;
import com.fffg.cex.marketdata.mapper.MarketDataMapper;
import com.fffg.cex.marketdata.vo.KlineVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * K 线聚合器（第二版：定时任务聚合 + 缓存）
 * <p>
 * 每 1 分钟从 trade_fill 聚合 1m K 线数据，后续 5m、15m、1h 等周期
 * 可以从 1m K 线进一步聚合（后续扩展）。
 */
@Slf4j
@Component
public class KlineAggregator {

    @Autowired
    private MarketDataMapper marketDataMapper;

    @Autowired
    private SymbolPairMapper symbolPairMapper;

    /** K 线内存缓存：symbol -> interval -> (openTime -> KlineVO) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<LocalDateTime, KlineVO>>> klineCache = new ConcurrentHashMap<>();

    /** 上次聚合的截止时间 */
    private LocalDateTime lastAggregateTime;

    /** 支持的 interval 列表 */
    private static final String[] SUPPORTED_INTERVALS = {"1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"};

    @PostConstruct
    public void init() {
        lastAggregateTime = LocalDateTime.now().minusHours(1);
        log.info("K线聚合器初始化完成，lastAggregateTime={}", lastAggregateTime);
    }

    /**
     * 每 1 分钟聚合一次 K 线
     */
    @Scheduled(fixedRate = 60000)
    public void aggregateKlines() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<String> symbols = symbolPairMapper.selectEnabledSymbols();

            for (String symbol : symbols) {
                for (String interval : SUPPORTED_INTERVALS) {
                    try {
                        long windowSeconds = getWindowSeconds(interval);
                        String sinceStr = lastAggregateTime.format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        List<KlineVO> klines = marketDataMapper.selectKlinesByWindow(
                                symbol, windowSeconds, sinceStr, 100);
                        updateCache(symbol, interval, klines);
                    } catch (Exception e) {
                        log.warn("聚合K线失败: symbol={}, interval={}, error={}",
                                symbol, interval, e.getMessage());
                    }
                }
            }

            lastAggregateTime = now;
            log.debug("K线聚合完成，最新聚合时间={}", lastAggregateTime);
        } catch (Exception e) {
            log.error("K线聚合异常", e);
        }
    }

    /**
     * 更新缓存
     */
    private void updateCache(String symbol, String interval, List<KlineVO> klines) {
        ConcurrentHashMap<String, ConcurrentHashMap<LocalDateTime, KlineVO>> symbolCache =
                klineCache.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<LocalDateTime, KlineVO> intervalCache =
                symbolCache.computeIfAbsent(interval, k -> new ConcurrentHashMap<>());

        for (KlineVO kline : klines) {
            intervalCache.put(kline.getOpenTime(), kline);
        }
    }

    /**
     * 获取缓存的 K 线数据
     *
     * @param symbol   交易对
     * @param interval 周期
     * @param limit    返回数量
     * @return K 线列表（按 openTime 降序）
     */
    public List<KlineVO> getKlines(String symbol, String interval, int limit) {
        ConcurrentHashMap<String, ConcurrentHashMap<LocalDateTime, KlineVO>> symbolCache =
                klineCache.get(symbol.toUpperCase());
        if (symbolCache == null) {
            return List.of();
        }
        ConcurrentHashMap<LocalDateTime, KlineVO> intervalCache = symbolCache.get(interval);
        if (intervalCache == null) {
            return List.of();
        }

        return intervalCache.values().stream()
                .sorted((a, b) -> b.getOpenTime().compareTo(a.getOpenTime()))
                .limit(limit)
                .toList();
    }

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
            default:    return 60;
        }
    }
}
