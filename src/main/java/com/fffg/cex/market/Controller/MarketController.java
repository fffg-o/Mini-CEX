package com.fffg.cex.market.Controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.market.Service.AssetsService;
import com.fffg.cex.market.Service.SymbolPairService;
import com.fffg.cex.market.VO.AssetVO;
import com.fffg.cex.market.VO.SymbolPairVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/markets")
public class MarketController {

    @Autowired
    private AssetsService assetsService;
    @Autowired
    private SymbolPairService symbolPairService;
    @GetMapping("/assets")
    public ApiResponse<List<AssetVO>> getAssets() {
        List<AssetVO> assetVO = assetsService.getAssetsList();
        return ApiResponse.success(assetVO);
    }
    @GetMapping("/symbols")
    public ApiResponse<List<SymbolPairVO>> getSymbolPairList(){
        return ApiResponse.success(symbolPairService.getList());
    }
}
