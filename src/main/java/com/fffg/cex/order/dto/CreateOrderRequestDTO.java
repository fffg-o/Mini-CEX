package com.fffg.cex.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequestDTO {

    @NotNull(message = "账户ID不能为空")
    private Long accountId;

    @NotBlank(message = "交易对不能为空")
    private String symbol;

    @NotBlank(message = "订单方向不能为空")
    @Pattern(regexp = "BUY|SELL", message = "订单方向只能是 BUY 或 SELL")
    private String side;

    @NotBlank(message = "订单类型不能为空")
    @Pattern(regexp = "LIMIT", message = "第一阶段只支持 LIMIT 限价单")
    private String orderType;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00000001", message = "价格必须大于0")
    private BigDecimal price;

    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.00000001", message = "数量必须大于0")
    private BigDecimal quantity;
}
