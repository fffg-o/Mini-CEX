package com.fffg.cex.market.Mapper;

import com.fffg.cex.market.VO.SymbolPairVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SymbolPairMapper {
    @Select("select symbol,base_asset,quote_asset,price_scale,quantity_scale,min_order_amount,status from symbol_pair ")
    List<SymbolPairVO> selectList();

    @Select("select symbol,base_asset,quote_asset,price_scale,quantity_scale,min_order_amount,status " +
            "from symbol_pair where symbol = #{symbol}")
    SymbolPairVO selectBySymbol(@Param("symbol") String symbol);
}
