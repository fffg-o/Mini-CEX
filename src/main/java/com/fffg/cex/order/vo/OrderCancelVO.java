package com.fffg.cex.order.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderCancelVO {
    private Long orderId;
    private String status;
}
