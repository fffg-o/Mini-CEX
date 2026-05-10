package com.fffg.cex.market.Service;

import com.fffg.cex.market.VO.SymbolPairVO;

import java.util.List;

public interface SymbolPairService {
    List<SymbolPairVO> getList();

    SymbolPairVO getBySymbol(String symbol);
}
