package com.fffg.cex.order.controller;

import com.fffg.cex.account.VO.PageVO;
import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.order.dto.CreateOrderRequestDTO;
import com.fffg.cex.order.service.OrderService;
import com.fffg.cex.order.vo.OrderCancelVO;
import com.fffg.cex.order.vo.OrderVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @Test
    void testCreateOrder() {
        CreateOrderRequestDTO request = new CreateOrderRequestDTO();
        request.setAccountId(100L);
        request.setSymbol("BTCUSDT");
        request.setSide("BUY");
        request.setOrderType("LIMIT");
        request.setPrice(new BigDecimal("50000"));
        request.setQuantity(new BigDecimal("0.001"));

        OrderVO mockOrder = new OrderVO();
        mockOrder.setOrderId(1L);
        mockOrder.setOrderNo("ORD001");
        mockOrder.setStatus("NEW");

        when(orderService.createOrder(request)).thenReturn(mockOrder);

        ApiResponse<OrderVO> response = orderController.createOrder(request);
        assertEquals(0, response.getCode());
        assertEquals("ORD001", response.getData().getOrderNo());
    }

    @Test
    void testGetOrder() {
        OrderVO mockOrder = new OrderVO();
        mockOrder.setOrderId(1L);
        mockOrder.setStatus("NEW");

        when(orderService.getOrderById(1L)).thenReturn(mockOrder);

        ApiResponse<OrderVO> response = orderController.getOrder(1L);
        assertEquals(0, response.getCode());
        assertEquals(1L, response.getData().getOrderId());
    }

    @Test
    void testGetOrders() {
        OrderVO order = new OrderVO();
        order.setOrderId(1L);
        PageVO<OrderVO> pageVO = new PageVO<>(List.of(order), 1, 10, 1);

        when(orderService.getOrdersByAccount(100L, "BTCUSDT", "BUY", "NEW", 1, 10))
                .thenReturn(pageVO);

        ApiResponse<PageVO<OrderVO>> response =
                orderController.getOrders(100L, "BTCUSDT", "BUY", "NEW", 1, 10);
        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().getTotal());
    }

    @Test
    void testCancelOrder() {
        OrderCancelVO cancelVO = new OrderCancelVO(1L, "CANCELED");

        when(orderService.cancelOrder(1L)).thenReturn(cancelVO);

        ApiResponse<OrderCancelVO> response = orderController.cancelOrder(1L);
        assertEquals(0, response.getCode());
        assertEquals("CANCELED", response.getData().getStatus());
    }
}
