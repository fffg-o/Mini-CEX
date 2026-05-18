package com.fffg.cex.wallet.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 充值地址 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepositAddressVO {
    private Long accountId;
    private String assetSymbol;
    private String chain;
    private String address;
}
