package com.fffg.cex.wallet.controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.wallet.dto.WithdrawRequestDTO;
import com.fffg.cex.wallet.service.WalletService;
import com.fffg.cex.wallet.vo.DepositAddressVO;
import com.fffg.cex.wallet.vo.DepositRecordVO;
import com.fffg.cex.wallet.vo.WithdrawRecordVO;
import com.fffg.cex.wallet.vo.WithdrawResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link WalletController} 的单元测试
 */
@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletController walletController;

    @Test
    void testGetDepositAddress_Success() {
        DepositAddressVO addressVO = new DepositAddressVO();
        addressVO.setAccountId(100L);
        addressVO.setAssetSymbol("USDT");
        addressVO.setChain("ETH");
        addressVO.setAddress("0x1234567890abcdef");

        when(walletService.getDepositAddress(100L, "USDT", "ETH"))
                .thenReturn(addressVO);

        ApiResponse<DepositAddressVO> response =
                walletController.getDepositAddress(100L, "USDT", "ETH");

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals("0x1234567890abcdef", response.getData().getAddress());
        assertEquals("USDT", response.getData().getAssetSymbol());
    }

    @Test
    void testGetDepositAddress_NewAddress() {
        DepositAddressVO addressVO = new DepositAddressVO();
        addressVO.setAccountId(200L);
        addressVO.setAssetSymbol("BTC");
        addressVO.setChain("BTC");
        addressVO.setAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");

        when(walletService.getDepositAddress(200L, "BTC", "BTC"))
                .thenReturn(addressVO);

        ApiResponse<DepositAddressVO> response =
                walletController.getDepositAddress(200L, "BTC", "BTC");

        assertEquals(0, response.getCode());
        assertEquals("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", response.getData().getAddress());
    }

    @Test
    void testGetDeposits_Success() {
        DepositRecordVO deposit = new DepositRecordVO();
        deposit.setDepositId(1L);
        deposit.setAccountId(100L);
        deposit.setAssetSymbol("USDT");
        deposit.setAmount(new BigDecimal("1000"));
        deposit.setStatus("SUCCESS");

        when(walletService.getDeposits(100L, "USDT", "SUCCESS", 1, 20))
                .thenReturn(List.of(deposit));

        ApiResponse<List<DepositRecordVO>> response =
                walletController.getDeposits(100L, "USDT", "SUCCESS", 1, 20);

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("USDT", response.getData().get(0).getAssetSymbol());
    }

    @Test
    void testGetDeposits_Empty() {
        when(walletService.getDeposits(100L, null, null, 1, 20))
                .thenReturn(List.of());

        ApiResponse<List<DepositRecordVO>> response =
                walletController.getDeposits(100L, null, null, 1, 20);

        assertEquals(0, response.getCode());
        assertTrue(response.getData().isEmpty());
    }

    @Test
    void testGetDeposits_DefaultPagination() {
        when(walletService.getDeposits(100L, "USDT", null, 1, 20))
                .thenReturn(List.of());

        // 使用默认分页
        ApiResponse<List<DepositRecordVO>> response =
                walletController.getDeposits(100L, "USDT", null, 1, 20);
        assertEquals(0, response.getCode());
        verify(walletService).getDeposits(100L, "USDT", null, 1, 20);
    }

    @Test
    void testApplyWithdraw_Success() {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setAccountId(100L);
        request.setAssetSymbol("USDT");
        request.setChain("ETH");
        request.setToAddress("0xReceiverAddress");
        request.setAmount(new BigDecimal("500"));
        request.setFee(new BigDecimal("10"));

        WithdrawResultVO resultVO = new WithdrawResultVO();
        resultVO.setWithdrawId(1L);
        resultVO.setStatus("REVIEWING");

        when(walletService.applyWithdraw(request)).thenReturn(resultVO);

        ApiResponse<WithdrawResultVO> response = walletController.applyWithdraw(request);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals(1L, response.getData().getWithdrawId());
        assertEquals("REVIEWING", response.getData().getStatus());
    }

    @Test
    void testApplyWithdraw_WithBusinessId() {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setAccountId(100L);
        request.setAssetSymbol("BTC");
        request.setChain("BTC");
        request.setToAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
        request.setAmount(new BigDecimal("0.5"));
        request.setFee(new BigDecimal("0.001"));
        request.setBusinessId("WITHDRAW_20250518_001");

        WithdrawResultVO resultVO = new WithdrawResultVO();
        resultVO.setWithdrawId(2L);
        resultVO.setStatus("REVIEWING");

        when(walletService.applyWithdraw(request)).thenReturn(resultVO);

        ApiResponse<WithdrawResultVO> response = walletController.applyWithdraw(request);

        assertEquals(0, response.getCode());
        assertEquals(2L, response.getData().getWithdrawId());
        verify(walletService).applyWithdraw(argThat(req ->
                "WITHDRAW_20250518_001".equals(req.getBusinessId())));
    }

    @Test
    void testGetWithdraws_Success() {
        WithdrawRecordVO record = new WithdrawRecordVO();
        record.setWithdrawId(1L);
        record.setAccountId(100L);
        record.setAssetSymbol("USDT");
        record.setAmount(new BigDecimal("500"));
        record.setStatus("REVIEWING");

        when(walletService.getWithdraws(100L, "REVIEWING", 1, 20))
                .thenReturn(List.of(record));

        ApiResponse<List<WithdrawRecordVO>> response =
                walletController.getWithdraws(100L, "REVIEWING", 1, 20);

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("REVIEWING", response.getData().get(0).getStatus());
    }

    @Test
    void testGetWithdraws_Empty() {
        when(walletService.getWithdraws(100L, null, 1, 20))
                .thenReturn(List.of());

        ApiResponse<List<WithdrawRecordVO>> response =
                walletController.getWithdraws(100L, null, 1, 20);

        assertEquals(0, response.getCode());
        assertTrue(response.getData().isEmpty());
    }

    @Test
    void testGetWithdraws_DefaultPagination() {
        when(walletService.getWithdraws(100L, "SUCCESS", 1, 20))
                .thenReturn(List.of());

        ApiResponse<List<WithdrawRecordVO>> response =
                walletController.getWithdraws(100L, "SUCCESS", 1, 20);
        assertEquals(0, response.getCode());
        verify(walletService).getWithdraws(100L, "SUCCESS", 1, 20);
    }
}
