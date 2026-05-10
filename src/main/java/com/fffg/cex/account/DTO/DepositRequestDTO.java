package com.fffg.cex.account.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepositRequestDTO {
    @NotBlank(message = "币种不能为空")
    private String assetSymbol;

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.00000001", message = "充值金额必须大于0")
    private BigDecimal amount;

    /**
     * 幂等键：用于防止重复充值。
     * 客户端每次充值请求应传入唯一值（如UUID），
     * 服务端根据此值判断是否已处理过该请求。
     * 如果不传，则由服务端自动生成。
     */
    private String businessId;
}
