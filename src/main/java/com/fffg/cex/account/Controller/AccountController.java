package com.fffg.cex.account.Controller;

import com.fffg.cex.account.DTO.CreateAccountRequestDTO;
import com.fffg.cex.account.DTO.DepositRequestDTO;
import com.fffg.cex.account.Service.AccountService;
import com.fffg.cex.account.VO.AccountBalanceVO;
import com.fffg.cex.account.VO.AccountVO;
import com.fffg.cex.account.VO.AssetLedgerVO;
import com.fffg.cex.account.VO.PageVO;
import com.fffg.cex.common.result.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @PostMapping("/")
    public ApiResponse<AccountVO> createAccount(@RequestBody @Valid CreateAccountRequestDTO createAccountRequestDTO){
        return ApiResponse.success(accountService.createAccount(createAccountRequestDTO));
    }

    @GetMapping("/{accountId}")
    public ApiResponse<AccountVO> getAccount(@PathVariable Long accountId) {
        return ApiResponse.success(accountService.getAccountById(accountId));
    }

    @GetMapping("/{accountId}/balances")
    public ApiResponse<List<AccountBalanceVO>> getBalances(@PathVariable Long accountId) {
        return ApiResponse.success(accountService.getBalances(accountId));
    }

    @PostMapping("/{accountId}/balances/deposit")
    public ApiResponse<Void> deposit(@PathVariable Long accountId, @RequestBody @Valid DepositRequestDTO request) {
        accountService.deposit(accountId, request);
        return ApiResponse.success();
    }

    @GetMapping("/{accountId}/ledgers")
    public ApiResponse<PageVO<AssetLedgerVO>> getLedgers(
            @PathVariable Long accountId,
            @RequestParam(required = false) String assetSymbol,
            @RequestParam(required = false) String businessType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(accountService.getLedgers(accountId, assetSymbol, businessType, pageNum, pageSize));
    }
}
