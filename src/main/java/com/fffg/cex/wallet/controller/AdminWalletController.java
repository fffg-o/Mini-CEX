package com.fffg.cex.wallet.controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 钱包管理端 Controller
 * <p>
 * 提供提现审核通过/拒绝等管理功能。
 */
@Tag(name = "Admin API", description = "管理端：提现审核")
@RestController
@RequestMapping("/admin/withdraws")
public class AdminWalletController {

    @Autowired
    private WalletService walletService;

    /**
     * 9.5 审核通过提现
     * POST /api/admin/withdraws/{withdrawId}/approve
     */
    @Operation(summary = "审核通过提现", description = "审核通过提现申请，将提现状态更新为 APPROVED")
    @PostMapping("/{withdrawId}/approve")
    public ApiResponse<Map<String, Object>> approveWithdraw(@PathVariable Long withdrawId) {
        walletService.approveWithdraw(withdrawId);
        return ApiResponse.success(Map.of("withdrawId", withdrawId, "status", "APPROVED"));
    }

    /**
     * 9.5 审核拒绝提现
     * POST /api/admin/withdraws/{withdrawId}/reject
     */
    @Operation(summary = "审核拒绝提现", description = "审核拒绝提现申请，释放之前冻结的资产")
    @PostMapping("/{withdrawId}/reject")
    public ApiResponse<Map<String, Object>> rejectWithdraw(@PathVariable Long withdrawId) {
        walletService.rejectWithdraw(withdrawId);
        return ApiResponse.success(Map.of("withdrawId", withdrawId, "status", "REJECTED"));
    }
}
