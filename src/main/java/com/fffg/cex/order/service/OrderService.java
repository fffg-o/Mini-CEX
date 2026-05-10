package com.fffg.cex.order.service;

import com.fffg.cex.account.VO.PageVO;
import com.fffg.cex.order.dto.CreateOrderRequestDTO;
import com.fffg.cex.order.vo.OrderCancelVO;
import com.fffg.cex.order.vo.OrderVO;

public interface OrderService {

    /**
     * 创建限价单
     */
    OrderVO createOrder(CreateOrderRequestDTO request);

    /**
     * 根据订单ID查询订单详情
     */
    OrderVO getOrderById(Long orderId);

    /**
     * 查询账户订单列表（分页）
     */
    PageVO<OrderVO> getOrdersByAccount(Long accountId, String symbol, String side, String status,
                                       int pageNum, int pageSize);

    /**
     * 撤销订单
     */
    OrderCancelVO cancelOrder(Long orderId);
}
