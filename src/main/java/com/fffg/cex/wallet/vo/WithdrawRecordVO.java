package com.fffg.cex.wallet.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现记录 VO
 */
@Data
public class WithdrawRecordVO {
    private Long withdrawId;
    private Long accountId;
    private String assetSymbol;
    private String chain;
    private String toAddress;
    private BigDecimal amount;
    private BigDecimal fee;
    private String status;
    private String txHash;
    private String businessId;
    private LocalDateTime createdAt;
}
