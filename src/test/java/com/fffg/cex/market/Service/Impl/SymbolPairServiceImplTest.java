package com.fffg.cex.market.Service.Impl;

import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.VO.SymbolPairVO;
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
class SymbolPairServiceImplTest {

    @Mock
    private SymbolPairMapper symbolPairMapper;

    @InjectMocks
    private SymbolPairServiceImpl symbolPairService;

    @Test
    void testGetList() {
        SymbolPairVO btcusdt = new SymbolPairVO();
        btcusdt.setSymbol("BTCUSDT");
        btcusdt.setBaseAsset("BTC");
        btcusdt.setQuoteAsset("USDT");

        when(symbolPairMapper.selectList()).thenReturn(List.of(btcusdt));

        List<SymbolPairVO> result = symbolPairService.getList();
        assertEquals(1, result.size());
        assertEquals("BTCUSDT", result.get(0).getSymbol());
    }

    @Test
    void testGetBySymbol() {
        SymbolPairVO btcusdt = new SymbolPairVO();
        btcusdt.setSymbol("BTCUSDT");
        btcusdt.setPriceScale(2);
        btcusdt.setQuantityScale(6);
        btcusdt.setMinOrderAmount(new BigDecimal("10"));

        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcusdt);

        SymbolPairVO result = symbolPairService.getBySymbol("BTCUSDT");
        assertNotNull(result);
        assertEquals("BTCUSDT", result.getSymbol());
        assertEquals(2, result.getPriceScale());
        assertEquals(new BigDecimal("10"), result.getMinOrderAmount());
    }

    @Test
    void testGetBySymbol_NotFound() {
        when(symbolPairMapper.selectBySymbol("UNKNOWN")).thenReturn(null);

        SymbolPairVO result = symbolPairService.getBySymbol("UNKNOWN");
        assertNull(result);
    }
}
