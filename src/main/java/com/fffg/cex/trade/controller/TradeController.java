package com.fffg.cex.trade.controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.trade.service.TradeService;
import com.fffg.cex.trade.vo.TradeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成交记录查询接口
 */
@Tag(name = "Trade API", description = "成交记录查询")
@RestController
public class TradeController {

    @Autowired
    private TradeService tradeService;

    /**
     * 查询某个交易对最近成交记录
     * GET /api/trades
     */
    @Operation(summary = "查询最近成交", description = "查询某个交易对最近成交记录")
    @GetMapping("/trades")
    public ApiResponse<List<TradeVO>> getRecentTrades(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(tradeService.getRecentTrades(symbol, limit));
    }
}
