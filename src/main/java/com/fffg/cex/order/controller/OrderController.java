package com.fffg.cex.order.controller;

import com.fffg.cex.account.VO.PageVO;
import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.order.dto.CreateOrderRequestDTO;
import com.fffg.cex.order.service.OrderService;
import com.fffg.cex.order.vo.OrderCancelVO;
import com.fffg.cex.order.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 创建限价单
     * POST /api/orders
     */
    @PostMapping("/orders")
    public ApiResponse<OrderVO> createOrder(@RequestBody @Valid CreateOrderRequestDTO request) {
        return ApiResponse.success(orderService.createOrder(request));
    }

    /**
     * 查询订单详情
     * GET /api/orders/{orderId}
     */
    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderVO> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.getOrderById(orderId));
    }

    /**
     * 查询账户订单列表（分页）
     * GET /api/accounts/{accountId}/orders
     */
    @GetMapping("/accounts/{accountId}/orders")
    public ApiResponse<PageVO<OrderVO>> getOrders(
            @PathVariable Long accountId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(
                orderService.getOrdersByAccount(accountId, symbol, side, status, pageNum, pageSize));
    }

    /**
     * 撤销订单
     * POST /api/orders/{orderId}/cancel
     */
    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<OrderCancelVO> cancelOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.cancelOrder(orderId));
    }
}
