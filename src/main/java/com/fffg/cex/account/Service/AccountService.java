package com.fffg.cex.account.Service;

import com.fffg.cex.account.DTO.CreateAccountRequestDTO;
import com.fffg.cex.account.DTO.DepositRequestDTO;
import com.fffg.cex.account.VO.AccountBalanceVO;
import com.fffg.cex.account.VO.AccountVO;
import com.fffg.cex.account.VO.AssetLedgerVO;
import com.fffg.cex.account.VO.PageVO;

import java.util.List;

public interface AccountService {
    AccountVO createAccount(CreateAccountRequestDTO createAccountRequestDTO);

    AccountVO getAccountById(Long accountId);

    List<AccountBalanceVO> getBalances(Long accountId);

    void deposit(Long accountId, DepositRequestDTO request);

    PageVO<AssetLedgerVO> getLedgers(Long accountId, String assetSymbol, String businessType, int pageNum, int pageSize);
}
