package com.fffg.cex.trade.service.Impl;

import com.fffg.cex.common.exception.BusinessException;
import com.fffg.cex.common.exception.ErrorCode;
import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.VO.SymbolPairVO;
import com.fffg.cex.trade.mapper.TradeMapper;
import com.fffg.cex.trade.service.TradeService;
import com.fffg.cex.trade.vo.TradeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TradeServiceImpl implements TradeService {

    @Autowired
    private TradeMapper tradeMapper;

    @Autowired
    private SymbolPairMapper symbolPairMapper;

    /** 默认返回数量 */
    private static final int DEFAULT_LIMIT = 50;

    /** 最大返回数量 */
    private static final int MAX_LIMIT = 200;

    @Override
    public List<TradeVO> getRecentTrades(String symbol, int limit) {
        // 1. 校验交易对存在
        SymbolPairVO symbolPair = symbolPairMapper.selectBySymbol(symbol.toUpperCase());
        if (symbolPair == null) {
            throw new BusinessException(ErrorCode.SYMBOL_NOT_FOUND.getCode(),
                    ErrorCode.SYMBOL_NOT_FOUND.getMessage());
        }

        // 2. 限制查询数量
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }

        // 3. 查询最近成交记录
        return tradeMapper.selectRecentBySymbol(symbol.toUpperCase(), limit);
    }
}
