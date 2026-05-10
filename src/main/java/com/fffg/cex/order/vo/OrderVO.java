package com.fffg.cex.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {
    private Long orderId;
    private String orderNo;
    private Long accountId;
    private String symbol;
    private String side;
    private String orderType;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private String status;
    private LocalDateTime createdAt;
}
