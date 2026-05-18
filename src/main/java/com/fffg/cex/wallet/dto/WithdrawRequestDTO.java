package com.fffg.cex.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现申请 DTO
 */
@Data
public class WithdrawRequestDTO {

    @NotNull(message = "账户ID不能为空")
    private Long accountId;

    @NotBlank(message = "币种不能为空")
    private String assetSymbol;

    @NotBlank(message = "链不能为空")
    private String chain;

    @NotBlank(message = "提现地址不能为空")
    private String toAddress;

    @NotNull(message = "提现金额不能为空")
    @DecimalMin(value = "0.00000001", message = "提现金额必须大于0")
    private BigDecimal amount;

    @NotNull(message = "手续费不能为空")
    @DecimalMin(value = "0", message = "手续费不能为负数")
    private BigDecimal fee;

    /**
     * 幂等键（可选），防止重复提交
     */
    private String businessId;
}
