package com.fffg.cex.trade.service.Impl;

import com.fffg.cex.common.exception.BusinessException;
import com.fffg.cex.common.exception.ErrorCode;
import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.VO.SymbolPairVO;
import com.fffg.cex.trade.mapper.TradeMapper;
import com.fffg.cex.trade.vo.TradeVO;
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
class TradeServiceImplTest {

    @Mock
    private TradeMapper tradeMapper;

    @Mock
    private SymbolPairMapper symbolPairMapper;

    @InjectMocks
    private TradeServiceImpl tradeService;

    private SymbolPairVO mockSymbolPair;

    @BeforeEach
    void setUp() {
        mockSymbolPair = new SymbolPairVO();
        mockSymbolPair.setSymbol("BTCUSDT");
    }

    @Test
    void testGetRecentTrades_Success() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        TradeVO trade = new TradeVO();
        trade.setTradeNo("TRD001");
        trade.setPrice(new BigDecimal("50000"));
        when(tradeMapper.selectRecentBySymbol("BTCUSDT", 50)).thenReturn(List.of(trade));

        List<TradeVO> result = tradeService.getRecentTrades("btcusdt", 50);
        assertEquals(1, result.size());
        assertEquals("TRD001", result.get(0).getTradeNo());
        verify(tradeMapper).selectRecentBySymbol("BTCUSDT", 50);
    }

    @Test
    void testGetRecentTrades_SymbolNotFound() {
        when(symbolPairMapper.selectBySymbol("UNKNOWN")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tradeService.getRecentTrades("unknown", 50));
        assertEquals(ErrorCode.SYMBOL_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testGetRecentTrades_LimitZeroUsesDefault() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        tradeService.getRecentTrades("BTCUSDT", 0);
        verify(tradeMapper).selectRecentBySymbol("BTCUSDT", 50);
    }

    @Test
    void testGetRecentTrades_LimitExceedsMax() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        tradeService.getRecentTrades("BTCUSDT", 999);
        verify(tradeMapper).selectRecentBySymbol("BTCUSDT", 200);
    }

    @Test
    void testGetRecentTrades_NegativeLimit() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        tradeService.getRecentTrades("BTCUSDT", -5);
        verify(tradeMapper).selectRecentBySymbol("BTCUSDT", 50);
    }

    @Test
    void testGetRecentTrades_NormalLimit() {
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        tradeService.getRecentTrades("BTCUSDT", 30);
        verify(tradeMapper).selectRecentBySymbol("BTCUSDT", 30);
    }
}
