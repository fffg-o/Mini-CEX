package com.fffg.cex.account.Service.Impl;

import com.fffg.cex.account.DTO.CreateAccountRequestDTO;
import com.fffg.cex.account.DTO.DepositRequestDTO;
import com.fffg.cex.account.Mapper.AccountBalanceMapper;
import com.fffg.cex.account.Mapper.AccountMapper;
import com.fffg.cex.account.Mapper.AssetLedgerMapper;
import com.fffg.cex.account.Mapper.AssetLedgerRecord;
import com.fffg.cex.account.Service.AccountService;
import com.fffg.cex.account.VO.AccountBalanceVO;
import com.fffg.cex.account.VO.AccountVO;
import com.fffg.cex.account.VO.AssetLedgerVO;
import com.fffg.cex.account.VO.PageVO;
import com.fffg.cex.common.exception.BusinessException;
import com.fffg.cex.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountBalanceMapper accountBalanceMapper;

    @Autowired
    private AssetLedgerMapper assetLedgerMapper;

    @Override
    public AccountVO createAccount(CreateAccountRequestDTO createAccountRequestDTO) {
         accountMapper.createAccount(createAccountRequestDTO);
         return accountMapper.getAccountById(createAccountRequestDTO.getId());
    }

    @Override
    public AccountVO getAccountById(Long accountId) {
        AccountVO account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }
        return account;
    }

    @Override
    public List<AccountBalanceVO> getBalances(Long accountId) {
        // 先校验账户是否存在
        AccountVO account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }
        return accountBalanceMapper.selectByAccountId(accountId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deposit(Long accountId, DepositRequestDTO request) {
        // 1. 校验账户存在
        AccountVO account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }

        String assetSymbol = request.getAssetSymbol();
        BigDecimal amount = request.getAmount();

        // 2. 查询币种是否存在且启用（通过市场模块的资产表校验）
        // 使用 accountBalance 表判断是否已有该币种记录，如果没有则先创建
        AccountBalanceVO balance = accountBalanceMapper.selectByAccountIdAndAsset(accountId, assetSymbol);
        BigDecimal beforeAvailable = BigDecimal.ZERO;
        BigDecimal beforeFrozen = BigDecimal.ZERO;

        if (balance == null) {
            // 账户没有该币种余额记录，先创建
            accountBalanceMapper.insertBalance(accountId, assetSymbol);
        } else {
            beforeAvailable = balance.getAvailableBalance();
            beforeFrozen = balance.getFrozenBalance();
        }

        // 3. 增加可用余额
        accountBalanceMapper.addAvailableBalance(accountId, assetSymbol, amount);

        BigDecimal afterAvailable = beforeAvailable.add(amount);
        BigDecimal afterFrozen = beforeFrozen;

        // 4. 插入流水记录
        AssetLedgerRecord record = new AssetLedgerRecord();
        record.setAccountId(accountId);
        record.setAssetSymbol(assetSymbol);
        record.setBusinessType("MOCK_DEPOSIT");
        record.setBusinessId("DEP" + System.currentTimeMillis());
        record.setChangeAvailable(amount);
        record.setChangeFrozen(BigDecimal.ZERO);
        record.setBeforeAvailable(beforeAvailable);
        record.setAfterAvailable(afterAvailable);
        record.setBeforeFrozen(beforeFrozen);
        record.setAfterFrozen(afterFrozen);
        assetLedgerMapper.insert(record);
    }

    @Override
    public PageVO<AssetLedgerVO> getLedgers(Long accountId, String assetSymbol, String businessType, int pageNum, int pageSize) {
        // 先校验账户是否存在
        AccountVO account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }

        int offset = (pageNum - 1) * pageSize;
        List<AssetLedgerVO> records = assetLedgerMapper.selectPage(accountId, assetSymbol, businessType, offset, pageSize);
        long total = assetLedgerMapper.countByCondition(accountId, assetSymbol, businessType);
        return new PageVO<>(records, pageNum, pageSize, total);
    }
}
