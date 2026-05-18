package com.fffg.cex.market.Service.Impl;

import com.fffg.cex.market.Mapper.AssetsMapper;
import com.fffg.cex.market.VO.AssetVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetsServiceImplTest {

    @Mock
    private AssetsMapper assetsMapper;

    @InjectMocks
    private AssetsServiceImpl assetsService;

    @Test
    void testGetAssetsList() {
        AssetVO btc = new AssetVO();
        btc.setSymbol("BTC");
        btc.setName("Bitcoin");
        btc.setStatus(1);

        AssetVO usdt = new AssetVO();
        usdt.setSymbol("USDT");
        usdt.setName("Tether");
        usdt.setStatus(1);

        when(assetsMapper.selectAssetsList()).thenReturn(List.of(btc, usdt));

        List<AssetVO> result = assetsService.getAssetsList();
        assertEquals(2, result.size());
        assertEquals("BTC", result.get(0).getSymbol());
        assertEquals("USDT", result.get(1).getSymbol());
        verify(assetsMapper).selectAssetsList();
    }

    @Test
    void testGetAssetsList_Empty() {
        when(assetsMapper.selectAssetsList()).thenReturn(List.of());

        List<AssetVO> result = assetsService.getAssetsList();
        assertTrue(result.isEmpty());
    }
}
