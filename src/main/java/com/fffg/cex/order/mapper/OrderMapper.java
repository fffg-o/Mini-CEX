package com.fffg.cex.order.mapper;

import com.fffg.cex.order.vo.OrderVO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface OrderMapper {

    @Insert("insert into trade_order(order_no, account_id, symbol, side, order_type, price, quantity, " +
            "filled_quantity, status, created_at, updated_at) " +
            "values(#{orderNo}, #{accountId}, #{symbol}, #{side}, #{orderType}, #{price}, #{quantity}, " +
            "0, 'NEW', now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "orderId")
    void insert(OrderVO orderVO);

    @Select("select id as orderId, order_no as orderNo, account_id as accountId, symbol, side, " +
            "order_type as orderType, price, quantity, filled_quantity as filledQuantity, " +
            "status, created_at as createdAt " +
            "from trade_order where id = #{orderId}")
    OrderVO selectById(@Param("orderId") Long orderId);

    @Select("<script>" +
            "select id as orderId, order_no as orderNo, account_id as accountId, symbol, side, " +
            "order_type as orderType, price, quantity, filled_quantity as filledQuantity, " +
            "status, created_at as createdAt " +
            "from trade_order " +
            "where account_id = #{accountId} " +
            "<if test='symbol != null and symbol != \"\"'> and symbol = #{symbol} </if>" +
            "<if test='side != null and side != \"\"'> and side = #{side} </if>" +
            "<if test='status != null and status != \"\"'> and status = #{status} </if>" +
            "order by id desc " +
            "limit #{offset}, #{limit}" +
            "</script>")
    List<OrderVO> selectPage(@Param("accountId") Long accountId,
                             @Param("symbol") String symbol,
                             @Param("side") String side,
                             @Param("status") String status,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    @Select("<script>" +
            "select count(*) from trade_order " +
            "where account_id = #{accountId} " +
            "<if test='symbol != null and symbol != \"\"'> and symbol = #{symbol} </if>" +
            "<if test='side != null and side != \"\"'> and side = #{side} </if>" +
            "<if test='status != null and status != \"\"'> and status = #{status} </if>" +
            "</script>")
    long countByCondition(@Param("accountId") Long accountId,
                          @Param("symbol") String symbol,
                          @Param("side") String side,
                          @Param("status") String status);

    @Update("update trade_order set status = #{status}, updated_at = now() where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 增量更新已成交数量（解决撮合过程中 DB 值与内存值不一致的问题）。
     * SQL: filled_quantity = filled_quantity + #{delta}
     */
    @Update("update trade_order set filled_quantity = filled_quantity + #{delta}, updated_at = now() " +
            "where id = #{id}")
    int incrementFilledQuantity(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * 查询所有活跃订单（用于启动时加载到内存订单簿）
     */
    @Select("select id as orderId, order_no as orderNo, account_id as accountId, symbol, side, " +
            "order_type as orderType, price, quantity, filled_quantity as filledQuantity, " +
            "status, created_at as createdAt " +
            "from trade_order where status in ('NEW', 'PARTIALLY_FILLED')")
    List<OrderVO> selectActiveOrders();
}
