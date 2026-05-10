package com.fffg.cex.account.VO;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AssetLedgerVO {
    private Long id;
    private String assetSymbol;
    private String businessType;
    private String businessId;
    private BigDecimal changeAvailable;
    private BigDecimal changeFrozen;
    private BigDecimal beforeAvailable;
    private BigDecimal afterAvailable;
    private BigDecimal beforeFrozen;
    private BigDecimal afterFrozen;
    private LocalDateTime createdAt;
}
