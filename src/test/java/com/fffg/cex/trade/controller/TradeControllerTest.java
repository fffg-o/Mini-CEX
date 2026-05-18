package com.fffg.cex.trade.controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.trade.service.TradeService;
import com.fffg.cex.trade.vo.TradeVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link TradeController} 的单元测试
 */
@ExtendWith(MockitoExtension.class)
class TradeControllerTest {

    @Mock
    private TradeService tradeService;

    @InjectMocks
    private TradeController tradeController;

    @Test
    void testGetRecentTrades_Success() {
        TradeVO trade = new TradeVO();
        trade.setTradeId(1L);
        trade.setTradeNo("TRD001");
        trade.setSymbol("BTCUSDT");
        trade.setPrice(new BigDecimal("50000.00"));
        trade.setQuantity(new BigDecimal("0.001"));
        trade.setAmount(new BigDecimal("50.00"));

        when(tradeService.getRecentTrades("BTCUSDT", 50))
                .thenReturn(List.of(trade));

        ApiResponse<List<TradeVO>> response = tradeController.getRecentTrades("BTCUSDT", 50);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        assertEquals("TRD001", response.getData().get(0).getTradeNo());
    }

    @Test
    void testGetRecentTrades_EmptyResult() {
        when(tradeService.getRecentTrades("ETHUSDT", 10))
                .thenReturn(List.of());

        ApiResponse<List<TradeVO>> response = tradeController.getRecentTrades("ETHUSDT", 10);

        assertEquals(0, response.getCode());
        assertTrue(response.getData().isEmpty());
    }

    @Test
    void testGetRecentTrades_DefaultLimit() {
        when(tradeService.getRecentTrades("BTCUSDT", 50))
                .thenReturn(List.of());

        // 不传 limit 默认 50
        ApiResponse<List<TradeVO>> response = tradeController.getRecentTrades("BTCUSDT", 50);
        assertEquals(0, response.getCode());
        verify(tradeService).getRecentTrades("BTCUSDT", 50);
    }
}
