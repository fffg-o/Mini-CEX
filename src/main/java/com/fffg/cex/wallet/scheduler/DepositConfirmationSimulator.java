package com.fffg.cex.wallet.scheduler;

import com.fffg.cex.account.Mapper.AccountBalanceMapper;
import com.fffg.cex.account.Mapper.AssetLedgerMapper;
import com.fffg.cex.account.Mapper.AssetLedgerRecord;
import com.fffg.cex.wallet.mapper.DepositRecordMapper;
import com.fffg.cex.wallet.vo.DepositRecordVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 充值确认模拟器
 * <p>
 * 每 5 秒模拟链上确认数递增，当确认数达到要求后自动到账。
 */
@Slf4j
@Component
public class DepositConfirmationSimulator {

    @Autowired
    private DepositRecordMapper depositRecordMapper;

    @Autowired
    private AccountBalanceMapper accountBalanceMapper;

    @Autowired
    private AssetLedgerMapper assetLedgerMapper;

    @PostConstruct
    public void init() {
        log.info("充值确认模拟器已启动，每 5 秒模拟一次确认");
    }

    /**
     * 每 5 秒模拟一次确认
     */
    @Scheduled(fixedRate = 5000)
    public void simulateConfirmations() {
        List<DepositRecordVO> pendingRecords = depositRecordMapper.selectPendingRecords();

        for (DepositRecordVO record : pendingRecords) {
            try {
                processConfirmation(record);
            } catch (Exception e) {
                log.error("处理充值确认异常: depositId={}, error={}", record.getDepositId(), e.getMessage());
            }
        }
    }

    /**
     * 处理单笔充值确认
     */
    @Transactional(rollbackFor = Exception.class)
    public void processConfirmation(DepositRecordVO record) {
        int newConfirmations = record.getConfirmations() + 1;

        if (newConfirmations >= record.getRequiredConfirmations()) {
            // 确认数达到要求，到账
            completeDeposit(record);
        } else {
            // 仅更新确认数
            depositRecordMapper.updateStatus(
                    record.getDepositId(),
                    newConfirmations,
                    "PENDING",
                    null
            );
            log.debug("充值确认数更新: depositId={}, confirmations={}/{}",
                    record.getDepositId(), newConfirmations, record.getRequiredConfirmations());
        }
    }

    /**
     * 充值到账处理
     */
    private void completeDeposit(DepositRecordVO record) {
        Long accountId = record.getAccountId();
        String assetSymbol = record.getAssetSymbol();
        BigDecimal amount = record.getAmount();

        // 1. 更新充值记录状态
        depositRecordMapper.updateStatus(
                record.getDepositId(),
                record.getRequiredConfirmations(),
                "SUCCESS",
                LocalDateTime.now()
        );

        // 2. 增加账户可用余额
        accountBalanceMapper.addAvailableBalance(accountId, assetSymbol, amount);

        // 3. 获取充值后的余额（用于流水记录）
        BigDecimal currentAvailable = accountBalanceMapper.getAvailableBalance(accountId, assetSymbol);

        // 4. 插入资产流水
        AssetLedgerRecord ledger = new AssetLedgerRecord();
        ledger.setAccountId(accountId);
        ledger.setAssetSymbol(assetSymbol);
        ledger.setBusinessType("CHAIN_DEPOSIT");
        ledger.setBusinessId("DEPOSIT-" + record.getDepositId());
        ledger.setChangeAvailable(amount);
        ledger.setChangeFrozen(BigDecimal.ZERO);
        ledger.setBeforeAvailable(currentAvailable.subtract(amount));
        ledger.setAfterAvailable(currentAvailable);
        ledger.setBeforeFrozen(BigDecimal.ZERO);
        ledger.setAfterFrozen(BigDecimal.ZERO);
        assetLedgerMapper.insert(ledger);

        log.info("充值到账: depositId={}, accountId={}, assetSymbol={}, amount={}",
                record.getDepositId(), accountId, assetSymbol, amount);
    }
}
