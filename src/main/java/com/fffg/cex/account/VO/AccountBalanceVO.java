package com.fffg.cex.account.VO;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountBalanceVO {
    private String assetSymbol;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
}
