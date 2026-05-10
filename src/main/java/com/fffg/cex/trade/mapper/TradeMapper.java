package com.fffg.cex.trade.mapper;

import com.fffg.cex.trade.vo.TradeVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 成交记录 Mapper
 */
@Mapper
public interface TradeMapper {

    /**
     * 插入成交记录
     */
    @Insert("insert into trade_fill(trade_no, symbol, buy_order_id, sell_order_id, " +
            "buy_account_id, sell_account_id, price, quantity, amount, buy_fee, sell_fee, created_at) " +
            "values(#{tradeNo}, #{symbol}, #{buyOrderId}, #{sellOrderId}, " +
            "#{buyAccountId}, #{sellAccountId}, #{price}, #{quantity}, #{amount}, #{buyFee}, #{sellFee}, now())")
    @Options(useGeneratedKeys = true, keyProperty = "tradeId")
    void insert(TradeVO tradeVO);

    /**
     * 查询某个交易对最近的成交记录
     */
    @Select("select id as tradeId, trade_no as tradeNo, symbol, price, quantity, amount, " +
            "buy_order_id as buyOrderId, sell_order_id as sellOrderId, " +
            "buy_account_id as buyAccountId, sell_account_id as sellAccountId, " +
            "buy_fee as buyFee, sell_fee as sellFee, created_at as createdAt " +
            "from trade_fill " +
            "where symbol = #{symbol} " +
            "order by id desc " +
            "limit #{limit}")
    List<TradeVO> selectRecentBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);
}
