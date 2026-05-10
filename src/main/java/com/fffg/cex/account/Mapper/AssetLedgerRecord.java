package com.fffg.cex.account.Mapper;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssetLedgerRecord {
    private Long accountId;
    private String assetSymbol;
    private String businessType;
    private String businessId;
    private BigDecimal changeAvailable;
    private BigDecimal changeFrozen;
    private BigDecimal beforeAvailable;
    private BigDecimal afterAvailable;
    private BigDecimal beforeFrozen;
    private BigDecimal afterFrozen;
}
