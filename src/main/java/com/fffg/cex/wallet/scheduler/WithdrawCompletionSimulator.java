package com.fffg.cex.wallet.scheduler;

import com.fffg.cex.account.Mapper.AccountBalanceMapper;
import com.fffg.cex.account.Mapper.AssetLedgerMapper;
import com.fffg.cex.account.Mapper.AssetLedgerRecord;
import com.fffg.cex.wallet.mapper.WithdrawRecordMapper;
import com.fffg.cex.wallet.vo.WithdrawRecordVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 提现完成模拟器
 * <p>
 * 每 30 秒模拟链上提现处理完成，将 APPROVED 状态的提现转为 COMPLETED，
 * 并从冻结余额中扣除资产。
 */
@Slf4j
@Component
public class WithdrawCompletionSimulator {

    @Autowired
    private WithdrawRecordMapper withdrawRecordMapper;

    @Autowired
    private AccountBalanceMapper accountBalanceMapper;

    @Autowired
    private AssetLedgerMapper assetLedgerMapper;

    @PostConstruct
    public void init() {
        log.info("提现完成模拟器已启动，每 30 秒模拟一次链上处理");
    }

    /**
     * 每 30 秒模拟一次链上提现完成
     */
    @Scheduled(fixedRate = 30000)
    public void simulateCompletion() {
        List<WithdrawRecordVO> approvedRecords = withdrawRecordMapper.selectByStatus("APPROVED");

        for (WithdrawRecordVO record : approvedRecords) {
            try {
                processCompletion(record);
            } catch (Exception e) {
                log.error("处理提现完成异常: withdrawId={}, error={}", record.getWithdrawId(), e.getMessage());
            }
        }
    }

    /**
     * 处理单笔提现完成
     */
    @Transactional(rollbackFor = Exception.class)
    public void processCompletion(WithdrawRecordVO record) {
        Long accountId = record.getAccountId();
        String assetSymbol = record.getAssetSymbol();
        BigDecimal totalFreeze = record.getAmount().add(record.getFee());

        // 1. 生成模拟 txHash
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "");

        // 2. 更新提现记录状态和 txHash
        int affected = withdrawRecordMapper.updateStatusWithTxHash(
                record.getWithdrawId(), "APPROVED", "COMPLETED", txHash);
        if (affected == 0) {
            log.warn("提现状态已变更，跳过: withdrawId={}", record.getWithdrawId());
            return;
        }

        // 3. 扣除冻结余额（从 frozen 中扣减，不经过 available）
        accountBalanceMapper.subtractFrozenBalance(accountId, assetSymbol, totalFreeze);

        // 4. 获取扣除后的冻结余额（用于流水记录）
        BigDecimal currentFrozen = accountBalanceMapper.getFrozenBalance(accountId, assetSymbol);

        // 5. 插入流水
        AssetLedgerRecord ledger = new AssetLedgerRecord();
        ledger.setAccountId(accountId);
        ledger.setAssetSymbol(assetSymbol);
        ledger.setBusinessType("WITHDRAW_COMPLETE");
        ledger.setBusinessId("WC-" + record.getWithdrawId());
        ledger.setChangeAvailable(BigDecimal.ZERO);
        ledger.setChangeFrozen(totalFreeze.negate());
        ledger.setBeforeAvailable(accountBalanceMapper.getAvailableBalance(accountId, assetSymbol));
        ledger.setAfterAvailable(accountBalanceMapper.getAvailableBalance(accountId, assetSymbol));
        ledger.setBeforeFrozen(currentFrozen.add(totalFreeze));
        ledger.setAfterFrozen(currentFrozen);
        assetLedgerMapper.insert(ledger);

        log.info("提现完成: withdrawId={}, accountId={}, assetSymbol={}, amount={}, txHash={}",
                record.getWithdrawId(), accountId, assetSymbol, record.getAmount(), txHash);
    }
}
