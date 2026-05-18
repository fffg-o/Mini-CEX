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
import com.fffg.cex.wallet.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountBalanceMapper accountBalanceMapper;

    @Mock
    private AssetLedgerMapper assetLedgerMapper;

    @Mock
    private AssetsMapper assetsMapper;

    @Mock
    private DepositAddressManager depositAddressManager;

    @Mock
    private DepositRecordMapper depositRecordMapper;

    @Mock
    private WithdrawRecordMapper withdrawRecordMapper;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Captor
    private ArgumentCaptor<AssetLedgerRecord> ledgerCaptor;

    @Captor
    private ArgumentCaptor<WithdrawRecordVO> withdrawCaptor;

    private AccountVO mockAccount;
    private AssetVO mockAsset;

    @BeforeEach
    void setUp() {
        mockAccount = new AccountVO();
        mockAccount.setAccountId(100L);
        mockAccount.setUserName("testuser");

        mockAsset = new AssetVO();
        mockAsset.setSymbol("USDT");
        mockAsset.setName("Tether");
        mockAsset.setScaleNum(2);
        mockAsset.setStatus(1);
    }

    // ==================== getDepositAddress ====================

    @Test
    void testGetDepositAddress_Success() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(depositAddressManager.getOrCreateAddress(100L, "USDT", "ETH"))
                .thenReturn("0x1234567890abcdef");

        DepositAddressVO result = walletService.getDepositAddress(100L, "USDT", "ETH");
        assertNotNull(result);
        assertEquals(100L, result.getAccountId());
        assertEquals("USDT", result.getAssetSymbol());
        assertEquals("ETH", result.getChain());
        assertEquals("0x1234567890abcdef", result.getAddress());
    }

    @Test
    void testGetDepositAddress_AccountNotFound() {
        when(accountMapper.getAccountById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.getDepositAddress(999L, "USDT", "ETH"));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testGetDepositAddress_AssetNotFound() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("UNKNOWN")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.getDepositAddress(100L, "UNKNOWN", "ETH"));
        assertEquals(ErrorCode.ASSET_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testGetDepositAddress_AssetNotEnabled() {
        mockAsset.setStatus(0);
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.getDepositAddress(100L, "USDT", "ETH"));
        assertEquals(ErrorCode.ASSET_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testGetDepositAddress_ChainBlank() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.getDepositAddress(100L, "USDT", ""));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void testGetDepositAddress_AssetSymbolToUpperCase() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(depositAddressManager.getOrCreateAddress(100L, "USDT", "ETH"))
                .thenReturn("addr");

        DepositAddressVO result = walletService.getDepositAddress(100L, "usdt", "eth");
        assertEquals("USDT", result.getAssetSymbol());
        assertEquals("ETH", result.getChain());
    }

    // ==================== getDeposits ====================

    @Test
    void testGetDeposits_AllParams() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);

        DepositRecordVO record = new DepositRecordVO();
        record.setDepositId(1L);
        when(depositRecordMapper.selectByAllParams(100L, "USDT", "SUCCESS"))
                .thenReturn(List.of(record));

        List<DepositRecordVO> result = walletService.getDeposits(100L, "USDT", "SUCCESS", 1, 20);
        assertEquals(1, result.size());
    }

    @Test
    void testGetDeposits_AccountNotFound() {
        when(accountMapper.getAccountById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.getDeposits(999L, null, null, 1, 20));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== applyWithdraw ====================

    @Test
    void testApplyWithdraw_SmallAmount_AutoApproved() {
        WithdrawRequestDTO request = createWithdrawRequest("100", "1");

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(accountBalanceMapper.freezeBalance(100L, "USDT", new BigDecimal("101")))
                .thenReturn(1);
        when(accountBalanceMapper.getAvailableBalance(100L, "USDT"))
                .thenReturn(new BigDecimal("500"));
        when(accountBalanceMapper.getFrozenBalance(100L, "USDT"))
                .thenReturn(new BigDecimal("101"));

        WithdrawResultVO result = walletService.applyWithdraw(request);

        assertNotNull(result);
        assertEquals("AUTO_APPROVED", result.getStatus());

        verify(withdrawRecordMapper).insert(withdrawCaptor.capture());
        WithdrawRecordVO captured = withdrawCaptor.getValue();
        assertEquals("AUTO_APPROVED", captured.getStatus());
        assertEquals(new BigDecimal("100"), captured.getAmount());
        assertEquals(new BigDecimal("1"), captured.getFee());

        verify(assetLedgerMapper).insert(ledgerCaptor.capture());
        assertEquals("WITHDRAW_FREEZE", ledgerCaptor.getValue().getBusinessType());
    }

    @Test
    void testApplyWithdraw_LargeAmount_Reviewing() {
        WithdrawRequestDTO request = createWithdrawRequest("50000", "10");

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(accountBalanceMapper.freezeBalance(100L, "USDT", new BigDecimal("50010")))
                .thenReturn(1);
        when(accountBalanceMapper.getAvailableBalance(100L, "USDT"))
                .thenReturn(new BigDecimal("100000"));
        when(accountBalanceMapper.getFrozenBalance(100L, "USDT"))
                .thenReturn(new BigDecimal("50010"));

        WithdrawResultVO result = walletService.applyWithdraw(request);
        assertEquals("REVIEWING", result.getStatus());

        verify(withdrawRecordMapper).insert(withdrawCaptor.capture());
        assertEquals("REVIEWING", withdrawCaptor.getValue().getStatus());
    }

    @Test
    void testApplyWithdraw_AccountNotFound() {
        WithdrawRequestDTO request = createWithdrawRequest("100", "1");
        when(accountMapper.getAccountById(100L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.applyWithdraw(request));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testApplyWithdraw_AssetNotFound() {
        WithdrawRequestDTO request = createWithdrawRequest("100", "1");
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.applyWithdraw(request));
        assertEquals(ErrorCode.ASSET_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testApplyWithdraw_InsufficientBalance() {
        WithdrawRequestDTO request = createWithdrawRequest("100", "1");
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(accountBalanceMapper.freezeBalance(100L, "USDT", new BigDecimal("101")))
                .thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.applyWithdraw(request));
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE.getCode(), ex.getCode());
    }

    @Test
    void testApplyWithdraw_DuplicateBusinessId() {
        WithdrawRequestDTO request = createWithdrawRequest("100", "1");
        request.setBusinessId("WD-001");

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);

        WithdrawRecordVO existing = new WithdrawRecordVO();
        existing.setWithdrawId(1L);
        existing.setStatus("AUTO_APPROVED");
        when(withdrawRecordMapper.selectByBusinessId("WD-001")).thenReturn(existing);

        WithdrawResultVO result = walletService.applyWithdraw(request);
        assertEquals(1L, result.getWithdrawId());
        assertEquals("AUTO_APPROVED", result.getStatus());

        verify(accountBalanceMapper, never()).freezeBalance(anyLong(), anyString(), any());
        verify(withdrawRecordMapper, never()).insert(any());
    }

    // ==================== getWithdraws ====================

    @Test
    void testGetWithdraws_WithStatus() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);

        WithdrawRecordVO record = new WithdrawRecordVO();
        record.setWithdrawId(1L);
        when(withdrawRecordMapper.selectByAccountAndStatus(100L, "REVIEWING"))
                .thenReturn(List.of(record));

        List<WithdrawRecordVO> result = walletService.getWithdraws(100L, "REVIEWING", 1, 20);
        assertEquals(1, result.size());
    }

    @Test
    void testGetWithdraws_WithoutStatus() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);

        WithdrawRecordVO record = new WithdrawRecordVO();
        record.setWithdrawId(1L);
        when(withdrawRecordMapper.selectByAccountId(100L)).thenReturn(List.of(record));

        List<WithdrawRecordVO> result = walletService.getWithdraws(100L, null, 1, 20);
        assertEquals(1, result.size());
    }

    @Test
    void testGetWithdraws_AccountNotFound() {
        when(accountMapper.getAccountById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.getWithdraws(999L, null, 1, 20));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== approveWithdraw ====================

    @Test
    void testApproveWithdraw_Success() {
        WithdrawRecordVO record = new WithdrawRecordVO();
        record.setWithdrawId(1L);
        record.setStatus("REVIEWING");
        when(withdrawRecordMapper.selectById(1L)).thenReturn(record);
        when(withdrawRecordMapper.updateStatus(1L, "REVIEWING", "APPROVED")).thenReturn(1);

        walletService.approveWithdraw(1L);
        verify(withdrawRecordMapper).updateStatus(1L, "REVIEWING", "APPROVED");
    }

    @Test
    void testApproveWithdraw_NotFound() {
        when(withdrawRecordMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.approveWithdraw(999L));
        assertEquals(40016, ex.getCode());
    }

    @Test
    void testApproveWithdraw_InvalidStatus() {
        WithdrawRecordVO record = new WithdrawRecordVO();
        record.setWithdrawId(1L);
        record.setStatus("COMPLETED");
        when(withdrawRecordMapper.selectById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.approveWithdraw(1L));
        assertEquals(40017, ex.getCode());
    }

    // ==================== rejectWithdraw ====================

    @Test
    void testRejectWithdraw_Success() {
        WithdrawRecordVO record = new WithdrawRecordVO();
        record.setWithdrawId(1L);
        record.setAccountId(100L);
        record.setAssetSymbol("USDT");
        record.setStatus("REVIEWING");
        record.setAmount(new BigDecimal("100"));
        record.setFee(new BigDecimal("1"));

        when(withdrawRecordMapper.selectById(1L)).thenReturn(record);
        when(withdrawRecordMapper.updateStatus(1L, "REVIEWING", "REJECTED")).thenReturn(1);
        when(accountBalanceMapper.unfreezeBalance(100L, "USDT", new BigDecimal("101")))
                .thenReturn(1);
        when(accountBalanceMapper.getAvailableBalance(100L, "USDT"))
                .thenReturn(new BigDecimal("1000"));
        when(accountBalanceMapper.getFrozenBalance(100L, "USDT"))
                .thenReturn(new BigDecimal("0"));

        walletService.rejectWithdraw(1L);

        verify(accountBalanceMapper).unfreezeBalance(100L, "USDT", new BigDecimal("101"));
        verify(assetLedgerMapper).insert(ledgerCaptor.capture());
        assertEquals("WITHDRAW_UNFREEZE", ledgerCaptor.getValue().getBusinessType());
        assertEquals(new BigDecimal("101"), ledgerCaptor.getValue().getChangeAvailable());
    }

    @Test
    void testRejectWithdraw_NotFound() {
        when(withdrawRecordMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.rejectWithdraw(999L));
        assertEquals(40016, ex.getCode());
    }

    @Test
    void testRejectWithdraw_InvalidStatus() {
        WithdrawRecordVO record = new WithdrawRecordVO();
        record.setWithdrawId(1L);
        record.setStatus("APPROVED");
        when(withdrawRecordMapper.selectById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.rejectWithdraw(1L));
        assertEquals(40017, ex.getCode());
    }

    // ==================== helper ====================

    private WithdrawRequestDTO createWithdrawRequest(String amount, String fee) {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setAccountId(100L);
        request.setAssetSymbol("USDT");
        request.setChain("ETH");
        request.setToAddress("0xabc123");
        request.setAmount(new BigDecimal(amount));
        request.setFee(new BigDecimal(fee));
        return request;
    }
}
