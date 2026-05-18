package com.fffg.cex.marketdata.controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.marketdata.service.MarketDataService;
import com.fffg.cex.marketdata.vo.KlineVO;
import com.fffg.cex.marketdata.vo.MarketDepthVO;
import com.fffg.cex.marketdata.vo.MarketTradeVO;
import com.fffg.cex.marketdata.vo.TickerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 行情模块 Controller
 * <p>
 * 提供订单簿、最新成交、ticker、K 线等行情数据的只读查询接口。
 */
@Tag(name = "Market Data API", description = "行情数据查询：订单簿、最新成交、ticker、K线")
@RestController
@RequestMapping("/market")
public class MarketDataController {

    @Autowired
    private MarketDataService marketDataService;

    /**
     * 8.1 查询订单簿深度
     * GET /api/market/depth?symbol=BTCUSDT&limit=20
     */
    @Operation(summary = "查询订单簿", description = "查询指定交易对的订单簿深度")
    @GetMapping("/depth")
    public ApiResponse<MarketDepthVO> getDepth(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "20") @Max(100) int limit) {
        return ApiResponse.success(marketDataService.getDepth(symbol, limit));
    }

    /**
     * 8.2 查询最新成交（行情精简版）
     * GET /api/market/trades?symbol=BTCUSDT&limit=50
     */
    @Operation(summary = "查询最新成交", description = "查询某个交易对最近成交记录（精简版，适合行情展示）")
    @GetMapping("/trades")
    public ApiResponse<List<MarketTradeVO>> getRecentTrades(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "50") @Max(200) int limit) {
        return ApiResponse.success(marketDataService.getRecentTrades(symbol, limit));
    }

    /**
     * 8.3 查询 24 小时 ticker
     * GET /api/market/ticker?symbol=BTCUSDT
     */
    @Operation(summary = "查询24小时Ticker", description = "查询某个交易对24小时行情概要")
    @GetMapping("/ticker")
    public ApiResponse<TickerVO> getTicker(@RequestParam String symbol) {
        return ApiResponse.success(marketDataService.getTicker(symbol));
    }

    /**
     * 8.4 查询 K 线数据
     * GET /api/market/klines?symbol=BTCUSDT&interval=1m&limit=100
     */
    @Operation(summary = "查询K线", description = "查询K线（蜡烛图）数据，用于前端绘制图表")
    @GetMapping("/klines")
    public ApiResponse<List<KlineVO>> getKlines(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam(defaultValue = "100") @Max(500) int limit) {
        return ApiResponse.success(marketDataService.getKlines(symbol, interval, limit));
    }
}
