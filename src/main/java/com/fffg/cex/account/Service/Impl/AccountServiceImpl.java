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
import com.fffg.cex.market.Mapper.AssetsMapper;
import com.fffg.cex.market.VO.AssetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountBalanceMapper accountBalanceMapper;

    @Autowired
    private AssetLedgerMapper assetLedgerMapper;

    @Autowired
    private AssetsMapper assetsMapper;

    /** 创建账户时默认初始化的币种 */
    private static final List<String> DEFAULT_INIT_ASSETS = Arrays.asList("USDT", "BTC", "ETH");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountVO createAccount(CreateAccountRequestDTO createAccountRequestDTO) {
        // 1. 校验用户名是否已存在
        AccountVO existing = accountMapper.getAccountByUsername(createAccountRequestDTO.getUsername());
        if (existing != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS.getCode(), ErrorCode.USERNAME_EXISTS.getMessage());
        }

        // 2. 创建账户
        accountMapper.createAccount(createAccountRequestDTO);
        Long accountId = createAccountRequestDTO.getId();

        // 3. 初始化默认币种余额（余额为0）
        for (String assetSymbol : DEFAULT_INIT_ASSETS) {
            accountBalanceMapper.insertBalance(accountId, assetSymbol);
        }

        return accountMapper.getAccountById(accountId);
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

        String assetSymbol = request.getAssetSymbol().toUpperCase();
        BigDecimal amount = request.getAmount();

        // 2. 校验币种存在且启用
        AssetVO asset = assetsMapper.selectBySymbol(assetSymbol);
        if (asset == null) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND.getCode(), ErrorCode.ASSET_NOT_FOUND.getMessage());
        }
        if (asset.getStatus() == null || asset.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND.getCode(), "币种未启用");
        }

        // 3. 校验金额小数位数不超过币种精度
        if (amount.scale() > asset.getScaleNum()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "金额小数位数不能超过" + asset.getScaleNum() + "位");
        }

        // 4. 幂等性校验：如果 businessId 已存在则直接返回
        String businessId = request.getBusinessId();
        if (businessId != null && !businessId.isBlank()) {
            AssetLedgerVO existingLedger = assetLedgerMapper.selectByBusinessId(businessId);
            if (existingLedger != null) {
                log.warn("重复的充值请求 businessId={}, accountId={}, assetSymbol={}", businessId, accountId, assetSymbol);
                return;
            }
        } else {
            // 如果没有传入 businessId，则自动生成
            businessId = "DEP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                    + accountId + assetSymbol;
        }

        // 5. 查询或创建余额记录
        AccountBalanceVO balance = accountBalanceMapper.selectByAccountIdAndAsset(accountId, assetSymbol);
        BigDecimal beforeAvailable;
        BigDecimal beforeFrozen;

        if (balance == null) {
            beforeAvailable = BigDecimal.ZERO;
            beforeFrozen = BigDecimal.ZERO;
            accountBalanceMapper.insertBalance(accountId, assetSymbol);
        } else {
            beforeAvailable = balance.getAvailableBalance();
            beforeFrozen = balance.getFrozenBalance();
        }

        // 6. 条件更新：增加可用余额（使用 WHERE 条件确保数据一致性）
        int updatedRows = accountBalanceMapper.addAvailableBalanceWithCheck(accountId, assetSymbol, amount);
        if (updatedRows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "更新余额失败");
        }

        BigDecimal afterAvailable = beforeAvailable.add(amount);
        BigDecimal afterFrozen = beforeFrozen;

        // 7. 插入流水记录
        AssetLedgerRecord record = new AssetLedgerRecord();
        record.setAccountId(accountId);
        record.setAssetSymbol(assetSymbol);
        record.setBusinessType("MOCK_DEPOSIT");
        record.setBusinessId(businessId);
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
