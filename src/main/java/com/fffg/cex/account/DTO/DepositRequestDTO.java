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
}
