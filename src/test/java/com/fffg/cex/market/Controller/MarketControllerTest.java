package com.fffg.cex.market.Controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.market.Service.AssetsService;
import com.fffg.cex.market.Service.SymbolPairService;
import com.fffg.cex.market.VO.AssetVO;
import com.fffg.cex.market.VO.SymbolPairVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketControllerTest {

    @Mock
    private AssetsService assetsService;

    @Mock
    private SymbolPairService symbolPairService;

    @InjectMocks
    private MarketController marketController;

    @Test
    void testGetAssets() {
        AssetVO btc = new AssetVO();
        btc.setSymbol("BTC");

        when(assetsService.getAssetsList()).thenReturn(List.of(btc));

        ApiResponse<List<AssetVO>> response = marketController.getAssets();
        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
    }

    @Test
    void testGetSymbolPairList() {
        SymbolPairVO pair = new SymbolPairVO();
        pair.setSymbol("BTCUSDT");

        when(symbolPairService.getList()).thenReturn(List.of(pair));

        ApiResponse<List<SymbolPairVO>> response = marketController.getSymbolPairList();
        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("BTCUSDT", response.getData().get(0).getSymbol());
    }
}
