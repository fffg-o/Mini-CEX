package com.fffg.cex.market.Service.Impl;

import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.Service.SymbolPairService;
import com.fffg.cex.market.VO.SymbolPairVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SymbolPairServiceImpl implements SymbolPairService {

    @Autowired
    private SymbolPairMapper symbolPairMapper;
    @Override
    public List<SymbolPairVO> getList() {
        return symbolPairMapper.selectList();
    }

    @Override
    public SymbolPairVO getBySymbol(String symbol) {
        return symbolPairMapper.selectBySymbol(symbol);
    }
}
