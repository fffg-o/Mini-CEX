package com.fffg.cex.wallet.service;

import com.fffg.cex.wallet.dto.WithdrawRequestDTO;
import com.fffg.cex.wallet.vo.*;

import java.util.List;

/**
 * 钱包模块服务接口
 */
public interface WalletService {

    // ==================== 充值地址 ====================

    /**
     * 获取或创建充值地址
     */
    DepositAddressVO getDepositAddress(Long accountId, String assetSymbol, String chain);

    // ==================== 充值记录 ====================

    /**
     * 查询充值记录
     */
    List<DepositRecordVO> getDeposits(Long accountId, String assetSymbol, String status,
                                       int pageNum, int pageSize);

    // ==================== 提现 ====================

    /**
     * 提交提现申请
     */
    WithdrawResultVO applyWithdraw(WithdrawRequestDTO request);

    /**
     * 查询提现记录
     */
    List<WithdrawRecordVO> getWithdraws(Long accountId, String status, int pageNum, int pageSize);

    // ==================== 管理端审核 ====================

    /**
     * 审核通过提现
     */
    void approveWithdraw(Long withdrawId);

    /**
     * 审核拒绝提现（解冻资产）
     */
    void rejectWithdraw(Long withdrawId);
}
