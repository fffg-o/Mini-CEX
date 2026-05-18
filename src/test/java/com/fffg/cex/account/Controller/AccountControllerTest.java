package com.fffg.cex.account.Controller;

import com.fffg.cex.account.DTO.CreateAccountRequestDTO;
import com.fffg.cex.account.DTO.DepositRequestDTO;
import com.fffg.cex.account.Service.AccountService;
import com.fffg.cex.account.VO.AccountBalanceVO;
import com.fffg.cex.account.VO.AccountVO;
import com.fffg.cex.account.VO.AssetLedgerVO;
import com.fffg.cex.account.VO.PageVO;
import com.fffg.cex.common.result.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    @Test
    void testCreateAccount() {
        CreateAccountRequestDTO request = new CreateAccountRequestDTO();
        request.setUsername("newuser");

        AccountVO mockAccount = new AccountVO();
        mockAccount.setAccountId(1L);
        mockAccount.setUserName("newuser");

        when(accountService.createAccount(request)).thenReturn(mockAccount);

        ApiResponse<AccountVO> response = accountController.createAccount(request);
        assertEquals(0, response.getCode());
        assertEquals("newuser", response.getData().getUserName());
    }

    @Test
    void testGetAccount() {
        AccountVO mockAccount = new AccountVO();
        mockAccount.setAccountId(100L);
        mockAccount.setUserName("testuser");

        when(accountService.getAccountById(100L)).thenReturn(mockAccount);

        ApiResponse<AccountVO> response = accountController.getAccount(100L);
        assertEquals(0, response.getCode());
        assertEquals(100L, response.getData().getAccountId());
    }

    @Test
    void testGetBalances() {
        AccountBalanceVO balance = new AccountBalanceVO();
        balance.setAssetSymbol("USDT");
        balance.setAvailableBalance(new BigDecimal("1000"));

        when(accountService.getBalances(100L)).thenReturn(List.of(balance));

        ApiResponse<List<AccountBalanceVO>> response = accountController.getBalances(100L);
        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("USDT", response.getData().get(0).getAssetSymbol());
    }

    @Test
    void testDeposit() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setAssetSymbol("USDT");
        request.setAmount(new BigDecimal("500"));

        ApiResponse<Void> response = accountController.deposit(100L, request);
        assertEquals(0, response.getCode());
        verify(accountService).deposit(100L, request);
    }

    @Test
    void testGetLedgers() {
        AssetLedgerVO ledger = new AssetLedgerVO();
        ledger.setId(1L);
        PageVO<AssetLedgerVO> pageVO = new PageVO<>(List.of(ledger), 1, 10, 1);

        when(accountService.getLedgers(100L, "USDT", "MOCK_DEPOSIT", 1, 10))
                .thenReturn(pageVO);

        ApiResponse<PageVO<AssetLedgerVO>> response =
                accountController.getLedgers(100L, "USDT", "MOCK_DEPOSIT", 1, 10);
        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().getTotal());
    }
}
