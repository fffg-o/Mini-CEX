package com.fffg.cex.marketdata.mapper;

import com.fffg.cex.marketdata.vo.KlineVO;
import com.fffg.cex.marketdata.vo.MarketTradeVO;
import com.fffg.cex.marketdata.vo.TickerVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 行情数据 Mapper
 * <p>
 * 负责从 trade_fill 表聚合查询订单簿、最新成交、ticker、K 线等数据。
 * 第一版直接从数据库聚合，适合数据量小的阶段快速验证。
 */
@Mapper
public interface MarketDataMapper {

    // ==================== 最新成交 ====================

    /**
     * 查询某个交易对最近成交记录（按成交时间倒序）
     */
    @Select("select id as tradeId, price, quantity, amount, created_at as createdAt " +
            "from trade_fill " +
            "where symbol = #{symbol} " +
            "order by id desc " +
            "limit #{limit}")
    List<MarketTradeVO> selectRecentTrades(@Param("symbol") String symbol, @Param("limit") int limit);

    // ==================== Ticker 24h ====================

    /**
     * 查询 24 小时 ticker 聚合数据
     *
     * @param symbol 交易对
     * @return TickerVO，如果没有数据则返回 null
     */
    @Select("SELECT " +
            "  (SELECT price FROM trade_fill " +
            "   WHERE symbol = #{symbol} AND created_at >= NOW() - INTERVAL 24 HOUR " +
            "   ORDER BY created_at DESC LIMIT 1) AS last_price, " +
            "  (SELECT price FROM trade_fill " +
            "   WHERE symbol = #{symbol} AND created_at >= NOW() - INTERVAL 24 HOUR " +
            "   ORDER BY created_at ASC LIMIT 1) AS open_price, " +
            "  MAX(price) AS high_price, " +
            "  MIN(price) AS low_price, " +
            "  SUM(quantity) AS volume, " +
            "  SUM(amount) AS amount " +
            "FROM trade_fill " +
            "WHERE symbol = #{symbol} " +
            "  AND created_at >= NOW() - INTERVAL 24 HOUR")
    TickerVO selectTicker(@Param("symbol") String symbol);

    /**
     * 获取某交易对最新的成交价（用于 ticker 兜底）
     */
    @Select("select price from trade_fill " +
            "where symbol = #{symbol} " +
            "order by id desc limit 1")
    java.math.BigDecimal selectLastPrice(@Param("symbol") String symbol);

    // ==================== K 线 ====================

    /**
     * 按时间窗口分组聚合 K 线数据（MySQL GROUP_CONCAT 方式）
     * <p>
     * 注意：此 SQL 依赖 MySQL 的 GROUP_CONCAT 和 UNIX_TIMESTAMP 函数。
     * 如果使用 H2 数据库测试需要改为其他实现。
     *
     * @param symbol     交易对
     * @param windowSeconds 时间窗口秒数（如 60=1m, 300=5m, 3600=1h, 86400=1d）
     * @param since      起始时间
     * @param limit      返回数量
     */
    @Select("SELECT " +
            "  ANY_VALUE(FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(created_at) / #{windowSeconds}) * #{windowSeconds})) AS openTime, " +
            "  SUBSTRING_INDEX(GROUP_CONCAT(CAST(price AS CHAR) ORDER BY created_at), ',', 1) AS openPrice, " +
            "  MAX(price) AS highPrice, " +
            "  MIN(price) AS lowPrice, " +
            "  SUBSTRING_INDEX(GROUP_CONCAT(CAST(price AS CHAR) ORDER BY created_at DESC), ',', 1) AS closePrice, " +
            "  SUM(quantity) AS volume, " +
            "  SUM(amount) AS amount " +
            "FROM trade_fill " +
            "WHERE symbol = #{symbol} AND created_at >= #{since} " +
            "GROUP BY FLOOR(UNIX_TIMESTAMP(created_at) / #{windowSeconds}) " +
            "ORDER BY openTime DESC LIMIT #{limit}")
    List<KlineVO> selectKlinesByWindow(@Param("symbol") String symbol,
                                       @Param("windowSeconds") long windowSeconds,
                                       @Param("since") String since,
                                       @Param("limit") int limit);
}
