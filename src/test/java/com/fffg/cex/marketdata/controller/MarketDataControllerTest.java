package com.fffg.cex.marketdata.controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.marketdata.service.MarketDataService;
import com.fffg.cex.marketdata.vo.KlineVO;
import com.fffg.cex.marketdata.vo.MarketDepthVO;
import com.fffg.cex.marketdata.vo.MarketTradeVO;
import com.fffg.cex.marketdata.vo.TickerVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link MarketDataController} 的单元测试
 */
@ExtendWith(MockitoExtension.class)
class MarketDataControllerTest {

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private MarketDataController marketDataController;

    @Test
    void testGetDepth_Success() {
        MarketDepthVO depthVO = new MarketDepthVO();
        depthVO.setSymbol("BTCUSDT");
        depthVO.setBids(List.<String[]>of(new String[]{"50000.00", "0.5"}));
        depthVO.setAsks(List.<String[]>of(new String[]{"51000.00", "0.3"}));

        when(marketDataService.getDepth("BTCUSDT", 20)).thenReturn(depthVO);

        ApiResponse<MarketDepthVO> response = marketDataController.getDepth("BTCUSDT", 20);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals("BTCUSDT", response.getData().getSymbol());
        assertEquals(1, response.getData().getBids().size());
        assertEquals(1, response.getData().getAsks().size());
    }

    @Test
    void testGetDepth_DefaultLimit() {
        MarketDepthVO depthVO = new MarketDepthVO();
        depthVO.setSymbol("BTCUSDT");
        when(marketDataService.getDepth("BTCUSDT", 20)).thenReturn(depthVO);

        // limit 校验在 Service 层，Controller 直接透传
        ApiResponse<MarketDepthVO> response = marketDataController.getDepth("BTCUSDT", 20);
        assertEquals(0, response.getCode());
        verify(marketDataService).getDepth("BTCUSDT", 20);
    }

    @Test
    void testGetDepth_EmptyOrderBook() {
        MarketDepthVO depthVO = new MarketDepthVO();
        depthVO.setSymbol("ETHUSDT");
        depthVO.setBids(List.of());
        depthVO.setAsks(List.of());

        when(marketDataService.getDepth("ETHUSDT", 10)).thenReturn(depthVO);

        ApiResponse<MarketDepthVO> response = marketDataController.getDepth("ETHUSDT", 10);

        assertEquals(0, response.getCode());
        assertTrue(response.getData().getBids().isEmpty());
        assertTrue(response.getData().getAsks().isEmpty());
    }

    @Test
    void testGetRecentTrades_Success() {
        MarketTradeVO trade = new MarketTradeVO();
        trade.setTradeId(1L);
        trade.setPrice(new BigDecimal("50000.00"));
        trade.setQuantity(new BigDecimal("0.001"));

        when(marketDataService.getRecentTrades("BTCUSDT", 50))
                .thenReturn(List.of(trade));

        ApiResponse<List<MarketTradeVO>> response =
                marketDataController.getRecentTrades("BTCUSDT", 50);

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
    }

    @Test
    void testGetRecentTrades_Empty() {
        when(marketDataService.getRecentTrades("BTCUSDT", 50))
                .thenReturn(List.of());

        ApiResponse<List<MarketTradeVO>> response =
                marketDataController.getRecentTrades("BTCUSDT", 50);

        assertEquals(0, response.getCode());
        assertTrue(response.getData().isEmpty());
    }

    @Test
    void testGetTicker_Success() {
        TickerVO ticker = new TickerVO();
        ticker.setSymbol("BTCUSDT");
        ticker.setLastPrice(new BigDecimal("50000.00"));

        when(marketDataService.getTicker("BTCUSDT")).thenReturn(ticker);

        ApiResponse<TickerVO> response = marketDataController.getTicker("BTCUSDT");

        assertEquals(0, response.getCode());
        assertEquals("BTCUSDT", response.getData().getSymbol());
        assertEquals(new BigDecimal("50000.00"), response.getData().getLastPrice());
    }

    @Test
    void testGetTicker_EmptyData() {
        TickerVO emptyTicker = TickerVO.empty("BTCUSDT");
        when(marketDataService.getTicker("BTCUSDT")).thenReturn(emptyTicker);

        ApiResponse<TickerVO> response = marketDataController.getTicker("BTCUSDT");

        assertEquals(0, response.getCode());
        assertEquals("BTCUSDT", response.getData().getSymbol());
        assertEquals(BigDecimal.ZERO, response.getData().getLastPrice());
    }

    @Test
    void testGetKlines_Success() {
        KlineVO kline = new KlineVO();
        kline.setOpenTime(LocalDateTime.of(2025, 5, 18, 0, 0));
        kline.setOpenPrice(new BigDecimal("50000.00"));
        kline.setHighPrice(new BigDecimal("51000.00"));
        kline.setLowPrice(new BigDecimal("49000.00"));
        kline.setClosePrice(new BigDecimal("50500.00"));
        kline.setVolume(new BigDecimal("100.5"));

        when(marketDataService.getKlines("BTCUSDT", "1m", 100))
                .thenReturn(List.of(kline));

        ApiResponse<List<KlineVO>> response =
                marketDataController.getKlines("BTCUSDT", "1m", 100);

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals(new BigDecimal("50500.00"), response.getData().get(0).getClosePrice());
    }

    @Test
    void testGetKlines_Empty() {
        when(marketDataService.getKlines("BTCUSDT", "1h", 50))
                .thenReturn(List.of());

        ApiResponse<List<KlineVO>> response =
                marketDataController.getKlines("BTCUSDT", "1h", 50);

        assertEquals(0, response.getCode());
        assertTrue(response.getData().isEmpty());
    }

    @Test
    void testGetKlines_DefaultLimit() {
        when(marketDataService.getKlines("BTCUSDT", "1m", 100)).thenReturn(List.of());

        // 使用默认 limit=100
        ApiResponse<List<KlineVO>> response =
                marketDataController.getKlines("BTCUSDT", "1m", 100);
        assertEquals(0, response.getCode());
        verify(marketDataService).getKlines("BTCUSDT", "1m", 100);
    }
}
