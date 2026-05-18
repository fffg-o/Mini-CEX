package com.fffg.cex.wallet.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提现申请结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawResultVO {
    private Long withdrawId;
    private String status;  // REVIEWING / APPROVED / REJECTED / COMPLETED / FAILED
}
