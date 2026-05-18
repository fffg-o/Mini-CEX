package com.fffg.cex.wallet.controller;

import com.fffg.cex.common.result.ApiResponse;
import com.fffg.cex.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link AdminWalletController} 的单元测试
 */
@ExtendWith(MockitoExtension.class)
class AdminWalletControllerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private AdminWalletController adminWalletController;

    @Test
    void testApproveWithdraw_Success() {
        doNothing().when(walletService).approveWithdraw(1L);

        ApiResponse<Map<String, Object>> response = adminWalletController.approveWithdraw(1L);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals(1L, response.getData().get("withdrawId"));
        assertEquals("APPROVED", response.getData().get("status"));
        verify(walletService).approveWithdraw(1L);
    }

    @Test
    void testRejectWithdraw_Success() {
        doNothing().when(walletService).rejectWithdraw(2L);

        ApiResponse<Map<String, Object>> response = adminWalletController.rejectWithdraw(2L);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals(2L, response.getData().get("withdrawId"));
        assertEquals("REJECTED", response.getData().get("status"));
        verify(walletService).rejectWithdraw(2L);
    }
}
