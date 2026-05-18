package com.fffg.cex.wallet.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值记录 VO
 */
@Data
public class DepositRecordVO {
    private Long depositId;
    private Long accountId;
    private String assetSymbol;
    private String chain;
    private String txHash;
    private BigDecimal amount;
    private Integer confirmations;
    private Integer requiredConfirmations;
    private String status;  // PENDING / SUCCESS / FAILED
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
}
