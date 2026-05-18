package com.fffg.cex.marketdata.service.Impl;

import com.fffg.cex.common.exception.BusinessException;
import com.fffg.cex.common.exception.ErrorCode;
import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.VO.SymbolPairVO;
import com.fffg.cex.marketdata.mapper.MarketDataMapper;
import com.fffg.cex.marketdata.vo.KlineVO;
import com.fffg.cex.marketdata.vo.MarketDepthVO;
import com.fffg.cex.marketdata.vo.MarketTradeVO;
import com.fffg.cex.marketdata.vo.TickerVO;
import com.fffg.cex.matching.OrderBook;
import com.fffg.cex.matching.OrderBookManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceImplTest {

    @Mock
    private MarketDataMapper marketDataMapper;

    @Mock
    private SymbolPairMapper symbolPairMapper;

    @Mock
    private OrderBookManager orderBookManager;

    @InjectMocks
    private MarketDataServiceImpl marketDataService;

    private SymbolPairVO mockSymbolPair;

    @BeforeEach
    void setUp() {
        mockSymbolPair = new SymbolPairVO();
        mockSymbolPair.setSymbol("BTCUSDT");
        mockSymbolPair.setStatus(1);
    }

    // ==================== getDepth ====================

    @Test
    void testGetDepth_Success() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        OrderBook mockOrderBook = mock(OrderBook.class);
        when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);

        List<String[]> bids = List.of(
                new String[]{"50000", "0.5"},
                new String[]{"49900", "1.0"}
        );
        List<String[]> asks = List.of(
                new String[]{"50100", "0.3"},
                new String[]{"50200", "0.7"}
        );
        when(mockOrderBook.getBidsSnapshot(20)).thenReturn(bids);
        when(mockOrderBook.getAsksSnapshot(20)).thenReturn(asks);

        MarketDepthVO result = marketDataService.getDepth("BTCUSDT", 20);
        assertEquals("BTCUSDT", result.getSymbol());
        assertEquals(2, result.getBids().size());
        assertEquals(2, result.getAsks().size());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testGetDepth_SymbolNotFound() {
        when(symbolPairMapper.selectBySymbol("UNKNOWN")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> marketDataService.getDepth("UNKNOWN", 20));
        assertEquals(ErrorCode.SYMBOL_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testGetDepth_LimitZeroUsesDefault() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        OrderBook mockOrderBook = mock(OrderBook.class);
        when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
        when(mockOrderBook.getBidsSnapshot(20)).thenReturn(List.of());
        when(mockOrderBook.getAsksSnapshot(20)).thenReturn(List.of());

        marketDataService.getDepth("BTCUSDT", 0);
        verify(mockOrderBook).getBidsSnapshot(20);
        verify(mockOrderBook).getAsksSnapshot(20);
    }

    @Test
    void testGetDepth_LimitExceedsMax() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        OrderBook mockOrderBook = mock(OrderBook.class);
        when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
        when(mockOrderBook.getBidsSnapshot(100)).thenReturn(List.of());
        when(mockOrderBook.getAsksSnapshot(100)).thenReturn(List.of());

        marketDataService.getDepth("BTCUSDT", 999);
        verify(mockOrderBook).getBidsSnapshot(100);
    }

    // ==================== getRecentTrades ====================

    @Test
    void testGetRecentTrades_Success() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        MarketTradeVO trade = new MarketTradeVO();
        trade.setPrice(new BigDecimal("50000"));
        when(marketDataMapper.selectRecentTrades("BTCUSDT", 50))
                .thenReturn(List.of(trade));

        List<MarketTradeVO> result = marketDataService.getRecentTrades("BTCUSDT", 50);
        assertEquals(1, result.size());
    }

    @Test
    void testGetRecentTrades_SymbolNotFound() {
        when(symbolPairMapper.selectBySymbol("UNKNOWN")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> marketDataService.getRecentTrades("UNKNOWN", 50));
        assertEquals(ErrorCode.SYMBOL_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testGetRecentTrades_LimitBounds() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        when(marketDataMapper.selectRecentTrades("BTCUSDT", 50)).thenReturn(List.of());
        when(marketDataMapper.selectRecentTrades("BTCUSDT", 200)).thenReturn(List.of());

        marketDataService.getRecentTrades("BTCUSDT", 0);  // uses default 50
        marketDataService.getRecentTrades("BTCUSDT", 500); // uses max 200
        marketDataService.getRecentTrades("BTCUSDT", 30);

        verify(marketDataMapper).selectRecentTrades("BTCUSDT", 50);
        verify(marketDataMapper).selectRecentTrades("BTCUSDT", 200);
        verify(marketDataMapper).selectRecentTrades("BTCUSDT", 30);
    }

    // ==================== getTicker ====================

    @Test
    void testGetTicker_Success() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        TickerVO ticker = new TickerVO();
        ticker.setSymbol("BTCUSDT");
        ticker.setOpenPrice(new BigDecimal("40000"));
        ticker.setLastPrice(new BigDecimal("50000"));
        ticker.setHighPrice(new BigDecimal("51000"));
        ticker.setLowPrice(new BigDecimal("39000"));
        ticker.setVolume(new BigDecimal("1000"));
        ticker.setAmount(new BigDecimal("45000000"));

        when(marketDataMapper.selectTicker("BTCUSDT")).thenReturn(ticker);

        TickerVO result = marketDataService.getTicker("BTCUSDT");
        assertNotNull(result);
        assertEquals(new BigDecimal("10000"), result.getPriceChange()); // 50000 - 40000
        assertEquals(new BigDecimal("25.00"), result.getPriceChangePercent()); // (10000/40000)*100 = 25.00
    }

    @Test
    void testGetTicker_NoData_ReturnsLastPriceOnly() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        when(marketDataMapper.selectTicker("BTCUSDT")).thenReturn(null);
        when(marketDataMapper.selectLastPrice("BTCUSDT")).thenReturn(new BigDecimal("50000"));

        TickerVO result = marketDataService.getTicker("BTCUSDT");
        assertEquals(new BigDecimal("50000"), result.getLastPrice());
        assertEquals(new BigDecimal("50000"), result.getOpenPrice());
        assertEquals(new BigDecimal("50000"), result.getHighPrice());
        assertEquals(new BigDecimal("50000"), result.getLowPrice());
        assertEquals(BigDecimal.ZERO, result.getVolume());
    }

    @Test
    void testGetTicker_NoDataAtAll() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        when(marketDataMapper.selectTicker("BTCUSDT")).thenReturn(null);
        when(marketDataMapper.selectLastPrice("BTCUSDT")).thenReturn(null);

        TickerVO result = marketDataService.getTicker("BTCUSDT");
        assertEquals("BTCUSDT", result.getSymbol());
        assertEquals(BigDecimal.ZERO, result.getLastPrice());
        assertEquals(BigDecimal.ZERO, result.getVolume());
    }

    @Test
    void testGetTicker_ZeroOpenPrice() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        TickerVO ticker = new TickerVO();
        ticker.setOpenPrice(BigDecimal.ZERO);
        ticker.setLastPrice(new BigDecimal("50000"));

        when(marketDataMapper.selectTicker("BTCUSDT")).thenReturn(ticker);

        TickerVO result = marketDataService.getTicker("BTCUSDT");
        assertEquals(BigDecimal.ZERO, result.getPriceChangePercent());
    }

    // ==================== getKlines ====================

    @Test
    void testGetKlines_Success() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        KlineVO kline = new KlineVO();
        kline.setOpenPrice(new BigDecimal("50000"));
        when(marketDataMapper.selectKlinesByWindow(eq("BTCUSDT"), eq(60L), anyString(), eq(100)))
                .thenReturn(List.of(kline));

        List<KlineVO> result = marketDataService.getKlines("BTCUSDT", "1m", 100);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("50000"), result.get(0).getOpenPrice());
    }

    @Test
    void testGetKlines_InvalidInterval() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> marketDataService.getKlines("BTCUSDT", "invalid", 100));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void testGetKlines_AllIntervals() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        when(marketDataMapper.selectKlinesByWindow(anyString(), anyLong(), anyString(), anyInt()))
                .thenReturn(List.of());

        marketDataService.getKlines("BTCUSDT", "1m", 10);
        marketDataService.getKlines("BTCUSDT", "5m", 10);
        marketDataService.getKlines("BTCUSDT", "15m", 10);
        marketDataService.getKlines("BTCUSDT", "30m", 10);
        marketDataService.getKlines("BTCUSDT", "1h", 10);
        marketDataService.getKlines("BTCUSDT", "4h", 10);
        marketDataService.getKlines("BTCUSDT", "1d", 10);
        marketDataService.getKlines("BTCUSDT", "1w", 10);

        verify(marketDataMapper, times(8)).selectKlinesByWindow(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    void testGetKlines_LimitBounds() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        when(marketDataMapper.selectKlinesByWindow(anyString(), anyLong(), anyString(), anyInt()))
                .thenReturn(List.of());

        marketDataService.getKlines("BTCUSDT", "1m", 0);   // uses default 100
        marketDataService.getKlines("BTCUSDT", "1m", 1000); // uses max 500
        marketDataService.getKlines("BTCUSDT", "1m", 50);

        verify(marketDataMapper).selectKlinesByWindow(anyString(), anyLong(), anyString(), eq(100));
        verify(marketDataMapper).selectKlinesByWindow(anyString(), anyLong(), anyString(), eq(500));
        verify(marketDataMapper).selectKlinesByWindow(anyString(), anyLong(), anyString(), eq(50));
    }

    @Test
    void testGetKlines_SymbolNotFound() {
        when(symbolPairMapper.selectBySymbol("UNKNOWN")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> marketDataService.getKlines("UNKNOWN", "1m", 100));
        assertEquals(ErrorCode.SYMBOL_NOT_FOUND.getCode(), ex.getCode());
    }
}
