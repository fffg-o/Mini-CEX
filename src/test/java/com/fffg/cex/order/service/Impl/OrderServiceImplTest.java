package com.fffg.cex.order.service.Impl;

import com.fffg.cex.account.Mapper.AccountBalanceMapper;
import com.fffg.cex.account.Mapper.AccountMapper;
import com.fffg.cex.account.Mapper.AssetLedgerMapper;
import com.fffg.cex.account.Mapper.AssetLedgerRecord;
import com.fffg.cex.account.VO.AccountBalanceVO;
import com.fffg.cex.account.VO.AccountVO;
import com.fffg.cex.account.VO.PageVO;
import com.fffg.cex.common.exception.BusinessException;
import com.fffg.cex.common.exception.ErrorCode;
import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.VO.SymbolPairVO;
import com.fffg.cex.matching.MatchEngine;
import com.fffg.cex.matching.MatchResult;
import com.fffg.cex.matching.OrderBook;
import com.fffg.cex.matching.OrderBookManager;
import com.fffg.cex.order.dto.CreateOrderRequestDTO;
import com.fffg.cex.order.mapper.OrderMapper;
import com.fffg.cex.order.service.OrderService;
import com.fffg.cex.order.vo.OrderCancelVO;
import com.fffg.cex.order.vo.OrderVO;
import com.fffg.cex.trade.mapper.TradeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountBalanceMapper accountBalanceMapper;

    @Mock
    private AssetLedgerMapper assetLedgerMapper;

    @Mock
    private SymbolPairMapper symbolPairMapper;

    @Mock
    private OrderBookManager orderBookManager;

    @Mock
    private MatchEngine matchEngine;

    @Mock
    private TradeMapper tradeMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Captor
    private ArgumentCaptor<AssetLedgerRecord> ledgerCaptor;

    private AccountVO mockAccount;
    private SymbolPairVO mockSymbolPair;
    private CreateOrderRequestDTO buyRequest;
    private CreateOrderRequestDTO sellRequest;
    private OrderVO mockOrder;

    @BeforeEach
    void setUp() {
        mockAccount = new AccountVO();
        mockAccount.setAccountId(100L);
        mockAccount.setUserName("testuser");

        mockSymbolPair = new SymbolPairVO();
        mockSymbolPair.setSymbol("BTCUSDT");
        mockSymbolPair.setBaseAsset("BTC");
        mockSymbolPair.setQuoteAsset("USDT");
        mockSymbolPair.setPriceScale(2);
        mockSymbolPair.setQuantityScale(6);
        mockSymbolPair.setMinOrderAmount(new BigDecimal("10"));
        mockSymbolPair.setStatus(1);

        buyRequest = new CreateOrderRequestDTO();
        buyRequest.setAccountId(100L);
        buyRequest.setSymbol("BTCUSDT");
        buyRequest.setSide("BUY");
        buyRequest.setOrderType("LIMIT");
        buyRequest.setPrice(new BigDecimal("50000.00"));
        buyRequest.setQuantity(new BigDecimal("0.001"));

        sellRequest = new CreateOrderRequestDTO();
        sellRequest.setAccountId(100L);
        sellRequest.setSymbol("BTCUSDT");
        sellRequest.setSide("SELL");
        sellRequest.setOrderType("LIMIT");
        sellRequest.setPrice(new BigDecimal("51000.00"));
        sellRequest.setQuantity(new BigDecimal("0.001"));

        mockOrder = new OrderVO();
        mockOrder.setOrderId(1L);
        mockOrder.setOrderNo("ORD20250518000001");
        mockOrder.setAccountId(100L);
        mockOrder.setSymbol("BTCUSDT");
        mockOrder.setSide("BUY");
        mockOrder.setOrderType("LIMIT");
        mockOrder.setPrice(new BigDecimal("50000.00"));
        mockOrder.setQuantity(new BigDecimal("0.001"));
        mockOrder.setFilledQuantity(BigDecimal.ZERO);
        mockOrder.setStatus("NEW");
    }

    // ==================== createOrder (Buy) ====================

    @Test
    void testCreateOrder_Buy_Success() {
        setupLock();
        doAnswer(invocation -> {
            OrderVO vo = invocation.getArgument(0);
            vo.setOrderId(1L);
            return null;
        }).when(orderMapper).insert(any(OrderVO.class));
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        AccountBalanceVO balance = new AccountBalanceVO();
        balance.setAvailableBalance(new BigDecimal("10000"));
        balance.setFrozenBalance(new BigDecimal("0"));
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT")).thenReturn(balance);
        when(accountBalanceMapper.freezeBalance(100L, "USDT", new BigDecimal("50.00"))).thenReturn(1);

        when(orderMapper.selectById(1L)).thenReturn(mockOrder);

        OrderBook mockOrderBook = mock(OrderBook.class);
        when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
        when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook))).thenReturn(List.of());

        OrderVO result = orderService.createOrder(buyRequest);

        assertNotNull(result);
        verify(orderMapper).insert(any(OrderVO.class));
        verify(accountBalanceMapper).freezeBalance(100L, "USDT", new BigDecimal("50.00"));
        verify(assetLedgerMapper).insert(any(AssetLedgerRecord.class));
    }

    @Test
    void testCreateOrder_Sell_Success() {
        setupLock();
        doAnswer(invocation -> {
            OrderVO vo = invocation.getArgument(0);
            vo.setOrderId(1L);
            return null;
        }).when(orderMapper).insert(any(OrderVO.class));
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        AccountBalanceVO balance = new AccountBalanceVO();
        balance.setAvailableBalance(new BigDecimal("1.0"));
        balance.setFrozenBalance(new BigDecimal("0"));
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "BTC")).thenReturn(balance);
        when(accountBalanceMapper.freezeBalance(100L, "BTC", new BigDecimal("0.001"))).thenReturn(1);

        mockOrder.setSide("SELL");
        when(orderMapper.selectById(1L)).thenReturn(mockOrder);

        OrderBook mockOrderBook = mock(OrderBook.class);
        when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
        when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook))).thenReturn(List.of());

        OrderVO result = orderService.createOrder(sellRequest);

        assertNotNull(result);
        verify(orderMapper).insert(any(OrderVO.class));
        verify(accountBalanceMapper).freezeBalance(100L, "BTC", new BigDecimal("0.001"));
    }

    @Test
    void testCreateOrder_AccountNotFound() {
        setupLock();
        when(accountMapper.getAccountById(999L)).thenReturn(null);
        buyRequest.setAccountId(999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyRequest));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testCreateOrder_SymbolNotFound() {
        setupLock();
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyRequest));
        assertEquals(ErrorCode.SYMBOL_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testCreateOrder_SymbolNotEnabled() {
        mockSymbolPair.setStatus(0);
        setupLock();
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyRequest));
        assertEquals(ErrorCode.SYMBOL_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testCreateOrder_InvalidOrderType() {
        setupLock();
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        buyRequest.setOrderType("MARKET");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyRequest));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void testCreateOrder_PriceScaleExceeds() {
        mockSymbolPair.setPriceScale(2);
        setupLock();
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        buyRequest.setPrice(new BigDecimal("50000.123"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyRequest));
        assertEquals(ErrorCode.PRICE_SCALE_INVALID.getCode(), ex.getCode());
    }

    @Test
    void testCreateOrder_QuantityScaleExceeds() {
        mockSymbolPair.setQuantityScale(4);
        setupLock();
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        buyRequest.setQuantity(new BigDecimal("0.0012345"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyRequest));
        assertEquals(ErrorCode.QUANTITY_SCALE_INVALID.getCode(), ex.getCode());
    }

    @Test
    void testCreateOrder_AmountTooSmall() {
        mockSymbolPair.setMinOrderAmount(new BigDecimal("100"));
        setupLock();
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        buyRequest.setPrice(new BigDecimal("100"));
        buyRequest.setQuantity(new BigDecimal("0.001")); // amount = 0.1 < 100

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyRequest));
        assertEquals(ErrorCode.ORDER_AMOUNT_TOO_SMALL.getCode(), ex.getCode());
    }

    @Test
    void testCreateOrder_InsufficientBalance() {
        setupLock();
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        AccountBalanceVO balance = new AccountBalanceVO();
        balance.setAvailableBalance(new BigDecimal("10"));
        balance.setFrozenBalance(new BigDecimal("0"));
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT")).thenReturn(balance);
        when(accountBalanceMapper.freezeBalance(100L, "USDT", new BigDecimal("50.00"))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyRequest));
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE.getCode(), ex.getCode());
    }

    @Test
    void testCreateOrder_BalanceRecordNotFound() {
        setupLock();
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT")).thenReturn(null);
        when(accountBalanceMapper.freezeBalance(100L, "USDT", new BigDecimal("50.00"))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.createOrder(buyRequest));
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE.getCode(), ex.getCode());
    }

    @Test
    void testCreateOrder_WithMatching() {
        setupLock();
        doAnswer(invocation -> {
            OrderVO vo = invocation.getArgument(0);
            vo.setOrderId(1L);
            return null;
        }).when(orderMapper).insert(any(OrderVO.class));
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        AccountBalanceVO balance = new AccountBalanceVO();
        balance.setAvailableBalance(new BigDecimal("10000"));
        balance.setFrozenBalance(new BigDecimal("0"));
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT")).thenReturn(balance);
        when(accountBalanceMapper.freezeBalance(100L, "USDT", new BigDecimal("50.00"))).thenReturn(1);

        OrderVO createdOrder = new OrderVO();
        createdOrder.setOrderId(1L);
        createdOrder.setOrderNo("ORD20250518000001");
        createdOrder.setAccountId(100L);
        createdOrder.setSymbol("BTCUSDT");
        createdOrder.setSide("BUY");
        createdOrder.setOrderType("LIMIT");
        createdOrder.setPrice(new BigDecimal("50000.00"));
        createdOrder.setQuantity(new BigDecimal("0.001"));
        createdOrder.setFilledQuantity(BigDecimal.ZERO);
        createdOrder.setStatus("NEW");

        OrderVO filledOrder = new OrderVO();
        filledOrder.setOrderId(1L);
        filledOrder.setOrderNo("ORD20250518000001");
        filledOrder.setAccountId(100L);
        filledOrder.setSymbol("BTCUSDT");
        filledOrder.setSide("BUY");
        filledOrder.setOrderType("LIMIT");
        filledOrder.setPrice(new BigDecimal("50000.00"));
        filledOrder.setQuantity(new BigDecimal("0.001"));
        filledOrder.setStatus("PARTIALLY_FILLED");
        filledOrder.setFilledQuantity(new BigDecimal("0.0005"));

        // After matching, re-query returns updated order
        // Call 1: line 194 in createOrder -> createdOrder (filled=0)
        // Call 2: updateOrderAfterTrade -> filledOrder  (filled=0.0005)
        // Call 3: line 204 in createOrder -> filledOrder
        when(orderMapper.selectById(1L)).thenReturn(createdOrder, filledOrder, filledOrder);

        // Mock incremental filled quantity update
        when(orderMapper.incrementFilledQuantity(eq(1L), any(BigDecimal.class))).thenReturn(1);
        when(orderMapper.incrementFilledQuantity(eq(2L), any(BigDecimal.class))).thenReturn(1);

        OrderBook mockOrderBook = mock(OrderBook.class);
        when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);

        MatchResult matchResult = new MatchResult();
        matchResult.setTradeNo("TRD20250518000001");
        matchResult.setSymbol("BTCUSDT");
        matchResult.setPrice(new BigDecimal("50000.00"));
        matchResult.setQuantity(new BigDecimal("0.0005"));
        matchResult.setAmount(new BigDecimal("25.00"));
        matchResult.setBuyOrderId(1L);
        matchResult.setSellOrderId(2L);
        matchResult.setBuyAccountId(100L);
        matchResult.setSellAccountId(101L);
        matchResult.setBuyOrderNo("ORD20250518000001");
        matchResult.setSellOrderNo("ORD20250518000002");
        matchResult.setBuyFee(new BigDecimal("0.025"));
        matchResult.setSellFee(new BigDecimal("0.0000005"));
        matchResult.setBuyFrozenAmount(new BigDecimal("25.00"));
        matchResult.setBuyActualAmount(new BigDecimal("25.00"));
        matchResult.setBuyRefundAmount(BigDecimal.ZERO);
        matchResult.setSellFrozenQuantity(new BigDecimal("0.0005"));
        matchResult.setSellActualQuantity(new BigDecimal("0.0005"));

        when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook))).thenReturn(List.of(matchResult));

        // Mock settlement queries
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT"))
                .thenReturn(balance);
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "BTC"))
                .thenReturn(null);
        when(accountBalanceMapper.subtractFrozenBalance(100L, "USDT", new BigDecimal("25.00")))
                .thenReturn(1);

        OrderVO result = orderService.createOrder(buyRequest);

        assertNotNull(result);
        verify(tradeMapper).insert(any());
        verify(orderMapper).incrementFilledQuantity(eq(1L), any(BigDecimal.class));
        verify(accountBalanceMapper, atLeastOnce()).addAvailableBalance(anyLong(), anyString(), any());
    }

    // ==================== getOrderById ====================

    @Test
    void testGetOrderById_Success() {
        when(orderMapper.selectById(1L)).thenReturn(mockOrder);

        OrderVO result = orderService.getOrderById(1L);
        assertNotNull(result);
        assertEquals(1L, result.getOrderId());
    }

    @Test
    void testGetOrderById_NotFound() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.getOrderById(999L));
        assertEquals(ErrorCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== getOrdersByAccount ====================

    @Test
    void testGetOrdersByAccount_Success() {
        when(accountMapper.getAccountById(100L)).thenReturn(mockAccount);

        OrderVO order1 = new OrderVO();
        order1.setOrderId(1L);
        when(orderMapper.selectPage(100L, "BTCUSDT", "BUY", "NEW", 0, 10))
                .thenReturn(List.of(order1));
        when(orderMapper.countByCondition(100L, "BTCUSDT", "BUY", "NEW"))
                .thenReturn(1L);

        PageVO<OrderVO> result = orderService.getOrdersByAccount(100L, "BTCUSDT", "BUY", "NEW", 1, 10);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    void testGetOrdersByAccount_AccountNotFound() {
        when(accountMapper.getAccountById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.getOrdersByAccount(999L, null, null, null, 1, 20));
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== cancelOrder ====================

    @Test
    void testCancelOrder_Success() {
        OrderVO newOrder = new OrderVO();
        newOrder.setOrderId(1L);
        newOrder.setOrderNo("ORD20250518000001");
        newOrder.setAccountId(100L);
        newOrder.setSymbol("BTCUSDT");
        newOrder.setSide("BUY");
        newOrder.setPrice(new BigDecimal("50000.00"));
        newOrder.setQuantity(new BigDecimal("0.001"));
        newOrder.setFilledQuantity(BigDecimal.ZERO);
        newOrder.setStatus("NEW");

        when(orderMapper.selectById(1L)).thenReturn(newOrder);
        setupLockForCancel("BTCUSDT");

        // Re-query inside lock
        when(orderMapper.selectById(1L)).thenReturn(newOrder);
        when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(mockSymbolPair);

        AccountBalanceVO balance = new AccountBalanceVO();
        balance.setAvailableBalance(new BigDecimal("9950"));
        balance.setFrozenBalance(new BigDecimal("50"));
        when(accountBalanceMapper.selectByAccountIdAndAsset(100L, "USDT")).thenReturn(balance);
        when(accountBalanceMapper.unfreezeBalance(100L, "USDT", new BigDecimal("50.00"))).thenReturn(1);

        OrderBook mockOrderBook = mock(OrderBook.class);
        when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);

        OrderCancelVO result = orderService.cancelOrder(1L);

        assertNotNull(result);
        assertEquals(1L, result.getOrderId());
        assertEquals("CANCELED", result.getStatus());
        verify(orderMapper).updateStatus(1L, "CANCELED");
        verify(mockOrderBook).removeOrder(any(OrderVO.class));
        verify(assetLedgerMapper).insert(any(AssetLedgerRecord.class));
    }

    @Test
    void testCancelOrder_NotFound() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(999L));
        assertEquals(ErrorCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void testCancelOrder_AlreadyFilled() {
        mockOrder.setStatus("FILLED");
        when(orderMapper.selectById(1L)).thenReturn(mockOrder);
        setupLockForCancel("BTCUSDT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L));
        assertEquals(ErrorCode.ORDER_FULLY_FILLED.getCode(), ex.getCode());
    }

    @Test
    void testCancelOrder_AlreadyCanceled() {
        mockOrder.setStatus("CANCELED");
        when(orderMapper.selectById(1L)).thenReturn(mockOrder);
        setupLockForCancel("BTCUSDT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancelOrder(1L));
        assertEquals(ErrorCode.ORDER_ALREADY_CANCELED.getCode(), ex.getCode());
    }

    // ==================== helper ====================

    private void setupLock() {
        when(orderBookManager.executeWithLock(eq("BTCUSDT"), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    private void setupLockForCancel(String symbol) {
        when(orderBookManager.executeWithLock(eq(symbol), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }
}
