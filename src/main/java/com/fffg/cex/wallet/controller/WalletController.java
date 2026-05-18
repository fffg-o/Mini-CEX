package com.fffg.cex.wallet.controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.wallet.dto.WithdrawRequestDTO;
import com.fffg.cex.wallet.service.WalletService;
import com.fffg.cex.wallet.vo.DepositAddressVO;
import com.fffg.cex.wallet.vo.DepositRecordVO;
import com.fffg.cex.wallet.vo.WithdrawRecordVO;
import com.fffg.cex.wallet.vo.WithdrawResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 钱包模块 Controller（用户端接口）
 * <p>
 * 提供充值地址查询、充值记录查询、提现申请、提现记录查询等功能。
 */
@Tag(name = "Wallet API", description = "钱包模块：充值地址、充值记录、提现申请、提现记录")
@RestController
@RequestMapping("/wallet")
public class WalletController {

    @Autowired
    private WalletService walletService;

    /**
     * 9.1 获取充值地址
     * GET /api/wallet/deposit-address?accountId=1&assetSymbol=USDT&chain=ETH
     */
    @Operation(summary = "获取充值地址", description = "为指定账户和币种生成/查询充值地址")
    @GetMapping("/deposit-address")
    public ApiResponse<DepositAddressVO> getDepositAddress(
            @RequestParam Long accountId,
            @RequestParam String assetSymbol,
            @RequestParam String chain) {
        return ApiResponse.success(walletService.getDepositAddress(accountId, assetSymbol, chain));
    }

    /**
     * 9.2 查询充值记录
     * GET /api/wallet/deposits?accountId=1&assetSymbol=USDT&status=SUCCESS&pageNum=1&pageSize=20
     */
    @Operation(summary = "查询充值记录", description = "查询指定账户的链上充值记录")
    @GetMapping("/deposits")
    public ApiResponse<List<DepositRecordVO>> getDeposits(
            @RequestParam Long accountId,
            @RequestParam(required = false) String assetSymbol,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(walletService.getDeposits(accountId, assetSymbol, status, pageNum, pageSize));
    }

    /**
     * 9.3 提现申请
     * POST /api/wallet/withdraws
     */
    @Operation(summary = "提现申请", description = "提交提现申请，系统先冻结资产，然后进入审核流程")
    @PostMapping("/withdraws")
    public ApiResponse<WithdrawResultVO> applyWithdraw(@Valid @RequestBody WithdrawRequestDTO request) {
        return ApiResponse.success(walletService.applyWithdraw(request));
    }

    /**
     * 9.4 查询提现记录
     * GET /api/wallet/withdraws?accountId=1&status=REVIEWING&pageNum=1&pageSize=20
     */
    @Operation(summary = "查询提现记录", description = "查询指定账户的提现记录列表")
    @GetMapping("/withdraws")
    public ApiResponse<List<WithdrawRecordVO>> getWithdraws(
            @RequestParam Long accountId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(walletService.getWithdraws(accountId, status, pageNum, pageSize));
    }
}
