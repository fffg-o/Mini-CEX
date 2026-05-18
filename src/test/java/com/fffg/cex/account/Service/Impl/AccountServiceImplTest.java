package com.fffg.cex.account.Service.Impl;

import com.fffg.cex.account.DTO.CreateAccountRequestDTO;
import com.fffg.cex.account.DTO.DepositRequestDTO;
import com.fffg.cex.account.Mapper.AccountBalanceMapper;
import com.fffg.cex.account.Mapper.AccountMapper;
import com.fffg.cex.account.Mapper.AssetLedgerMapper;
import com.fffg.cex.account.Mapper.AssetLedgerRecord;
import com.fffg.cex.account.VO.AccountBalanceVO;
import com.fffg.cex.account.VO.AccountVO;
import com.fffg.cex.account.VO.AssetLedgerVO;
import com.fffg.cex.account.VO.PageVO;
import com.fffg.cex.common.exception.BusinessException;
import com.fffg.cex.common.exception.ErrorCode;
import com.fffg.cex.market.Mapper.AssetsMapper;
import com.fffg.cex.market.VO.AssetVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountBalanceMapper accountBalanceMapper;

    @Mock
    private AssetLedgerMapper assetLedgerMapper;

    @Mock
    private AssetsMapper assetsMapper;

    @InjectMocks
    private AccountServiceImpl accountService;

    @Captor
    private ArgumentCaptor<AssetLedgerRecord> ledgerCaptor;

    private CreateAccountRequestDTO createRequest;
    private AccountVO mockAccount;
    private AssetVO mockAsset;

    @BeforeEach
    void setUp() {
        createRequest = new CreateAccountRequestDTO();
        createRequest.setUsername("testuser");

        mockAccount = new AccountVO();
        mockAccount.setAccountId(100L);
        mockAccount.setUserName("testuser");
        mockAccount.setStatus(1);

        mockAsset = new AssetVO();
        mockAsset.setSymbol("USDT");
        mockAsset.setStatus(1);
        mockAsset.setScaleNum(2);
    }

    // ==================== createAccount ====================

    @Test
    void testCreateAccount_Success() {
        createRequest.setId(100L);
        when(accountMapper.getAccountByUsername("testuser")).thenReturn(null);
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);

        accountService.createAccount(createRequest);

        verify(accountMapper).createAccount(createRequest);
        verify(accountBalanceMapper).insertBalance(100L, "USDT");
        verify(accountBalanceMapper).insertBalance(100L, "BTC");
        verify(accountBalanceMapper).insertBalance(100L, "ETH");
        verify(accountMapper).getAccountById(100L);
    }

    @Test
    void testCreateAccount_UsernameExists() {
        when(accountMapper.getAccountByUsername("testuser")).thenReturn(mockAccount);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.createAccount(createRequest));
        assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), ex.getCode());
        assertEquals(ErrorCode.USERNAME_EXISTS.getMessage(), ex.getMessage());
        verify(accountMapper, never()).createAccount(any());
    }

    // ==================== getAccountById ====================

    @Test
    void testGetAccountById_Success() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);

        AccountVO result = accountService.getAccountById(100L);
        assertNotNull(result);
        assertEquals(100L, result.getAccountId());
        assertEquals("testuser", result.getUserName());
    }

    @Test
    void testGetAccountById_NotFound() {
        when(accountMapper.getAccountById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.getAccountById(999L));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== getBalances ====================

    @Test
    void testGetBalances_Success() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        List<AccountBalanceVO> balances = Arrays.asList(
                createBalance("USDT", "1000", "0"),
                createBalance("BTC", "1", "0")
        );
        when(accountBalanceMapper.selectByAccountId(100L)).thenReturn(balances);

        List<AccountBalanceVO> result = accountService.getBalances(100L);
        assertEquals(2, result.size());
        assertEquals("USDT", result.get(0).getAssetSymbol());
    }

    @Test
    void testGetBalances_AccountNotFound() {
        when(accountMapper.getAccountById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.getBalances(999L));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
        verify(accountBalanceMapper, never()).selectByAccountId(anyLong());
    }

    // ==================== deposit ====================

    @Test
    void testDeposit_Success() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("USDT");
        request.setAmount(new BigDecimal("500.00"));
        request.setBusinessId("DEP-001");

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(assetLedgerMapper.selectByBusinessId("DEP-001")).thenReturn(null);
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT"))
                .thenReturn(createBalance("USDT", "1000", "0"));
        when(accountBalanceMapper.addAvailableBalanceWithCheck(100L, "USDT", new BigDecimal("500.00")))
                .thenReturn(1);

        accountService.deposit(100L, request);

        verify(assetLedgerMapper).insert(ledgerCaptor.capture());
        AssetLedgerRecord record = ledgerCaptor.getValue();
        assertEquals(100L, record.getAccountId());
        assertEquals("USDT", record.getAssetSymbol());
        assertEquals("MOCK_DEPOSIT", record.getBusinessType());
        assertEquals("DEP-001", record.getBusinessId());
        assertEquals(0, new BigDecimal("500.00").compareTo(record.getChangeAvailable()));
        assertEquals(0, new BigDecimal("1000").compareTo(record.getBeforeAvailable()));
        assertEquals(0, new BigDecimal("1500").compareTo(record.getAfterAvailable()));
    }

    @Test
    void testDeposit_AccountNotFound() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("USDT");
        request.setAmount(new BigDecimal("100"));

        when(accountMapper.getAccountById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.deposit(999L, request));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testDeposit_AssetNotFound() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("XXX");
        request.setAmount(new BigDecimal("100"));

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("XXX")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.deposit(100L, request));
        assertEquals(ErrorCode.ASSET_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testDeposit_AssetNotEnabled() {
        mockAsset.setStatus(0);
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("USDT");
        request.setAmount(new BigDecimal("100"));

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.deposit(100L, request));
        assertEquals(ErrorCode.ASSET_NOT_FOUND.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("未启用"));
    }

    @Test
    void testDeposit_ScaleExceedsLimit() {
        mockAsset.setScaleNum(2);
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("USDT");
        request.setAmount(new BigDecimal("100.123"));

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.deposit(100L, request));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void testDeposit_DuplicateBusinessId() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("USDT");
        request.setAmount(new BigDecimal("500"));
        request.setBusinessId("DEP-001");

        AssetLedgerVO existingLedger = new AssetLedgerVO();
        existingLedger.setId(1L);
        existingLedger.setBusinessId("DEP-001");

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(assetLedgerMapper.selectByBusinessId("DEP-001")).thenReturn(existingLedger);

        accountService.deposit(100L, request);

        // 幂等校验通过，不执行后续操作
        verify(accountBalanceMapper, never()).addAvailableBalanceWithCheck(anyLong(), anyString(), any());
        verify(assetLedgerMapper, never()).insert(any());
    }

    @Test
    void testDeposit_NewBalanceRecord() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("USDT");
        request.setAmount(new BigDecimal("100"));
        request.setBusinessId("DEP-NEW");

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(assetLedgerMapper.selectByBusinessId("DEP-NEW")).thenReturn(null);
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT")).thenReturn(null);
        when(accountBalanceMapper.addAvailableBalanceWithCheck(100L, "USDT", new BigDecimal("100")))
                .thenReturn(1);

        accountService.deposit(100L, request);

        // 余额记录不存在时应该插入新记录
        verify(accountBalanceMapper).insertBalance(100L, "USDT");
        verify(assetLedgerMapper).insert(ledgerCaptor.capture());
        assertEquals(BigDecimal.ZERO, ledgerCaptor.getValue().getBeforeAvailable());
    }

    @Test
    void testDeposit_GenerateBusinessIdWhenNotProvided() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("USDT");
        request.setAmount(new BigDecimal("100"));

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT"))
                .thenReturn(createBalance("USDT", "500", "0"));
        when(accountBalanceMapper.addAvailableBalanceWithCheck(100L, "USDT", new BigDecimal("100")))
                .thenReturn(1);

        accountService.deposit(100L, request);

        verify(assetLedgerMapper).insert(ledgerCaptor.capture());
        String businessId = ledgerCaptor.getValue().getBusinessId();
        assertNotNull(businessId);
        assertTrue(businessId.startsWith("DEP"));
    }

    @Test
    void testDeposit_UpdateBalanceFailed() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("USDT");
        request.setAmount(new BigDecimal("100"));

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT"))
                .thenReturn(createBalance("USDT", "500", "0"));
        when(accountBalanceMapper.addAvailableBalanceWithCheck(100L, "USDT", new BigDecimal("100")))
                .thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.deposit(100L, request));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void testDeposit_AssetSymbolToUpperCase() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("usdt");
        request.setAmount(new BigDecimal("100"));
        request.setBusinessId("DEP-UPPER");

        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(assetsMapper.selectBySymbol("USDT")).thenReturn(mockAsset);
        when(assetLedgerMapper.selectByBusinessId("DEP-UPPER")).thenReturn(null);
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT"))
                .thenReturn(createBalance("USDT", "500", "0"));
        when(accountBalanceMapper.addAvailableBalanceWithCheck(100L, "USDT", new BigDecimal("100")))
                .thenReturn(1);

        accountService.deposit(100L, request);
        verify(assetLedgerMapper).insert(ledgerCaptor.capture());
        assertEquals("USDT", ledgerCaptor.getValue().getAssetSymbol());
    }

    // ==================== getLedgers ====================

    @Test
    void testGetLedgers_Success() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);

        AssetLedgerVO ledger1 = new AssetLedgerVO();
        ledger1.setId(1L);
        List<AssetLedgerVO> ledgers = List.of(ledger1);
        when(assetLedgerMapper.selectPage(100L, "USDT", "MOCK_DEPOSIT", 0, 10))
                .thenReturn(ledgers);
        when(assetLedgerMapper.countByCondition(100L, "USDT", "MOCK_DEPOSIT"))
                .thenReturn(1L);

        PageVO<AssetLedgerVO> result = accountService.getLedgers(100L, "USDT", "MOCK_DEPOSIT", 1, 10);
        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
    }

    @Test
    void testGetLedgers_AccountNotFound() {
        when(accountMapper.getAccountById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accountService.getLedgers(999L, null, null, 1, 20));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== helper ====================

    private AccountBalanceVO createBalance(String asset, String available, String frozen) {
        AccountBalanceVO vo = new AccountBalanceVO();
        vo.setAssetSymbol(asset);
        vo.setAvailableBalance(new BigDecimal(available));
        vo.setFrozenBalance(new BigDecimal(frozen));
        return vo;
    }
}
