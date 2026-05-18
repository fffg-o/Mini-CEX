package com.fffg.cex.wallet.service.Impl;

import com.fffg.cex.account.Mapper.AccountBalanceMapper;
import com.fffg.cex.account.Mapper.AccountMapper;
import com.fffg.cex.account.Mapper.AssetLedgerMapper;
import com.fffg.cex.account.Mapper.AssetLedgerRecord;
import com.fffg.cex.account.VO.AccountVO;
import com.fffg.cex.common.exception.BusinessException;
import com.fffg.cex.common.exception.ErrorCode;
import com.fffg.cex.market.Mapper.AssetsMapper;
import com.fffg.cex.market.VO.AssetVO;
import com.fffg.cex.wallet.dto.WithdrawRequestDTO;
import com.fffg.cex.wallet.manager.DepositAddressManager;
import com.fffg.cex.wallet.mapper.DepositRecordMapper;
import com.fffg.cex.wallet.mapper.WithdrawRecordMapper;
import com.fffg.cex.wallet.service.WalletService;
import com.fffg.cex.wallet.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 钱包模块服务实现
 */
@Slf4j
@Service
public class WalletServiceImpl implements WalletService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountBalanceMapper accountBalanceMapper;

    @Autowired
    private AssetLedgerMapper assetLedgerMapper;

    @Autowired
    private AssetsMapper assetsMapper;

    @Autowired
    private DepositAddressManager depositAddressManager;

    @Autowired
    private DepositRecordMapper depositRecordMapper;

    @Autowired
    private WithdrawRecordMapper withdrawRecordMapper;

    /** 大额提现阈值（USDT） */
    private static final BigDecimal LARGE_WITHDRAW_THRESHOLD = new BigDecimal("10000");

    // ==================== 充值地址 ====================

    @Override
    public DepositAddressVO getDepositAddress(Long accountId, String assetSymbol, String chain) {
        // 1. 校验账户存在
        AccountVO account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(),
                    ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }

        // 2. 校验币种存在且启用
        AssetVO asset = assetsMapper.selectBySymbol(assetSymbol.toUpperCase());
        if (asset == null || asset.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND.getCode(),
                    ErrorCode.ASSET_NOT_FOUND.getMessage());
        }

        // 3. 校验链参数
        if (chain == null || chain.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "链参数不能为空");
        }

        // 4. 获取或创建地址
        String address = depositAddressManager.getOrCreateAddress(accountId, assetSymbol.toUpperCase(), chain.toUpperCase());

        DepositAddressVO vo = new DepositAddressVO();
        vo.setAccountId(accountId);
        vo.setAssetSymbol(assetSymbol.toUpperCase());
        vo.setChain(chain.toUpperCase());
        vo.setAddress(address);
        return vo;
    }

    // ==================== 充值记录 ====================

    @Override
    public List<DepositRecordVO> getDeposits(Long accountId, String assetSymbol, String status,
                                              int pageNum, int pageSize) {
        // 校验账户存在
        AccountVO account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(),
                    ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }

        List<DepositRecordVO> records;
        if (assetSymbol != null && !assetSymbol.isBlank() && status != null && !status.isBlank()) {
            records = depositRecordMapper.selectByAllParams(accountId, assetSymbol.toUpperCase(), status.toUpperCase());
        } else if (assetSymbol != null && !assetSymbol.isBlank()) {
            records = depositRecordMapper.selectByAccountAndAsset(accountId, assetSymbol.toUpperCase());
        } else if (status != null && !status.isBlank()) {
            records = depositRecordMapper.selectByAccountAndStatus(accountId, status.toUpperCase());
        } else {
            records = depositRecordMapper.selectByAccountId(accountId);
        }

        return records;
    }

    // ==================== 提现 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawResultVO applyWithdraw(WithdrawRequestDTO request) {
        Long accountId = request.getAccountId();
        String assetSymbol = request.getAssetSymbol().toUpperCase();

        // 1. 校验账户存在
        AccountVO account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(),
                    ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }

        // 2. 校验币种存在且启用
        AssetVO asset = assetsMapper.selectBySymbol(assetSymbol);
        if (asset == null || asset.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND.getCode(),
                    ErrorCode.ASSET_NOT_FOUND.getMessage());
        }

        // 3. 幂等校验
        if (request.getBusinessId() != null && !request.getBusinessId().isBlank()) {
            WithdrawRecordVO exist = withdrawRecordMapper.selectByBusinessId(request.getBusinessId());
            if (exist != null) {
                WithdrawResultVO vo = new WithdrawResultVO();
                vo.setWithdrawId(exist.getWithdrawId());
                vo.setStatus(exist.getStatus());
                return vo;
            }
        }

        // 4. 计算总冻结金额
        BigDecimal totalFreeze = request.getAmount().add(request.getFee());

        // 5. 条件更新冻结资产（防止超扣）
        int affected = accountBalanceMapper.freezeBalance(accountId, assetSymbol, totalFreeze);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE.getCode(), "可用余额不足");
        }

        // 6. 生成 businessId
        String businessId = request.getBusinessId() != null ? request.getBusinessId() : UUID.randomUUID().toString();

        // 7. 创建提现记录
        WithdrawRecordVO withdrawRecord = new WithdrawRecordVO();
        withdrawRecord.setAccountId(accountId);
        withdrawRecord.setAssetSymbol(assetSymbol);
        withdrawRecord.setChain(request.getChain().toUpperCase());
        withdrawRecord.setToAddress(request.getToAddress());
        withdrawRecord.setAmount(request.getAmount());
        withdrawRecord.setFee(request.getFee());
        withdrawRecord.setBusinessId(businessId);

        // 判断是否大额提现
        boolean isLarge = request.getAmount().compareTo(LARGE_WITHDRAW_THRESHOLD) >= 0;
        String status = isLarge ? "REVIEWING" : "AUTO_APPROVED";
        withdrawRecord.setStatus(status);
        withdrawRecordMapper.insert(withdrawRecord);

        // 8. 获取冻结前后的余额（用于流水记录）
        BigDecimal currentAvailable = accountBalanceMapper.getAvailableBalance(accountId, assetSymbol);
        BigDecimal currentFrozen = accountBalanceMapper.getFrozenBalance(accountId, assetSymbol);

        // 9. 生成资产流水（WITHDRAW_FREEZE）
        AssetLedgerRecord ledger = new AssetLedgerRecord();
        ledger.setAccountId(accountId);
        ledger.setAssetSymbol(assetSymbol);
        ledger.setBusinessType("WITHDRAW_FREEZE");
        ledger.setBusinessId(businessId);
        ledger.setChangeAvailable(totalFreeze.negate());
        ledger.setChangeFrozen(totalFreeze);
        // 流水记录的是变化前的余额
        ledger.setBeforeAvailable(currentAvailable.add(totalFreeze));
        ledger.setBeforeFrozen(currentFrozen.subtract(totalFreeze));
        ledger.setAfterAvailable(currentAvailable);
        ledger.setAfterFrozen(currentFrozen);
        assetLedgerMapper.insert(ledger);

        log.info("提现申请成功: withdrawId={}, accountId={}, assetSymbol={}, amount={}, status={}",
                withdrawRecord.getWithdrawId(), accountId, assetSymbol, request.getAmount(), status);

        return new WithdrawResultVO(withdrawRecord.getWithdrawId(), status);
    }

    @Override
    public List<WithdrawRecordVO> getWithdraws(Long accountId, String status, int pageNum, int pageSize) {
        AccountVO account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(),
                    ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }

        if (status != null && !status.isBlank()) {
            return withdrawRecordMapper.selectByAccountAndStatus(accountId, status.toUpperCase());
        }
        return withdrawRecordMapper.selectByAccountId(accountId);
    }

    // ==================== 管理端审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveWithdraw(Long withdrawId) {
        WithdrawRecordVO record = withdrawRecordMapper.selectById(withdrawId);
        if (record == null) {
            throw new BusinessException(40016, "提现记录不存在");
        }
        if (!"REVIEWING".equals(record.getStatus()) && !"AUTO_APPROVED".equals(record.getStatus())) {
            throw new BusinessException(40017, "提现状态非法，仅 REVIEWING 或 AUTO_APPROVED 状态可审核通过");
        }

        int affected = withdrawRecordMapper.updateStatus(withdrawId, record.getStatus(), "APPROVED");
        if (affected == 0) {
            throw new BusinessException(40017, "提现状态已变更，请刷新后重试");
        }

        log.info("提现审核通过: withdrawId={}", withdrawId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectWithdraw(Long withdrawId) {
        WithdrawRecordVO record = withdrawRecordMapper.selectById(withdrawId);
        if (record == null) {
            throw new BusinessException(40016, "提现记录不存在");
        }
        if (!"REVIEWING".equals(record.getStatus())) {
            throw new BusinessException(40017, "提现状态非法，仅 REVIEWING 状态可审核拒绝");
        }

        // 1. 更新状态为 REJECTED
        int affected = withdrawRecordMapper.updateStatus(withdrawId, "REVIEWING", "REJECTED");
        if (affected == 0) {
            throw new BusinessException(40017, "提现状态已变更，请刷新后重试");
        }

        // 2. 解冻资产
        BigDecimal totalFreeze = record.getAmount().add(record.getFee());
        accountBalanceMapper.unfreezeBalance(record.getAccountId(), record.getAssetSymbol(), totalFreeze);

        // 3. 获取解冻后的余额（用于流水记录）
        BigDecimal currentAvailable = accountBalanceMapper.getAvailableBalance(record.getAccountId(), record.getAssetSymbol());
        BigDecimal currentFrozen = accountBalanceMapper.getFrozenBalance(record.getAccountId(), record.getAssetSymbol());

        // 4. 插入解冻流水
        AssetLedgerRecord ledger = new AssetLedgerRecord();
        ledger.setAccountId(record.getAccountId());
        ledger.setAssetSymbol(record.getAssetSymbol());
        ledger.setBusinessType("WITHDRAW_UNFREEZE");
        ledger.setBusinessId("WU-" + withdrawId);
        ledger.setChangeAvailable(totalFreeze);
        ledger.setChangeFrozen(totalFreeze.negate());
        ledger.setBeforeAvailable(currentAvailable.subtract(totalFreeze));
        ledger.setBeforeFrozen(currentFrozen.add(totalFreeze));
        ledger.setAfterAvailable(currentAvailable);
        ledger.setAfterFrozen(currentFrozen);
        assetLedgerMapper.insert(ledger);

        log.info("提现审核拒绝，资产已解冻: withdrawId={}, accountId={}, assetSymbol={}, amount={}",
                withdrawId, record.getAccountId(), record.getAssetSymbol(), totalFreeze);
    }
}
