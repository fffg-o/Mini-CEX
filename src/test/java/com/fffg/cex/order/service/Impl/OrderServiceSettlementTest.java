package com.fffg.cex.order.service.Impl;

import com.fffg.cex.account.Mapper.AccountBalanceMapper;
import com.fffg.cex.account.Mapper.AccountMapper;
import com.fffg.cex.account.Mapper.AssetLedgerMapper;
import com.fffg.cex.account.Mapper.AssetLedgerRecord;
import com.fffg.cex.account.VO.AccountBalanceVO;
import com.fffg.cex.account.VO.AccountVO;
import com.fffg.cex.market.Mapper.SymbolPairMapper;
import com.fffg.cex.market.VO.SymbolPairVO;
import com.fffg.cex.matching.MatchEngine;
import com.fffg.cex.matching.MatchResult;
import com.fffg.cex.matching.OrderBook;
import com.fffg.cex.matching.OrderBookManager;
import com.fffg.cex.order.dto.CreateOrderRequestDTO;
import com.fffg.cex.order.mapper.OrderMapper;
import com.fffg.cex.order.vo.OrderVO;
import com.fffg.cex.trade.mapper.TradeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/**
 * 订单结算专项测试：覆盖 {@link OrderServiceImpl} 中私有方法
 * processMatchResults / settleBuyer / settleSeller / updateOrderAfterTrade 的业务逻辑。
 * <p>
 * 私有方法通过公共入口 createOrder + mock MatchResult 间接验证。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单结算 & 价差退款 专项测试")
class OrderServiceSettlementTest {

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
    private SymbolPairVO btcUsdt;
    private OrderBook mockOrderBook;
    private AccountBalanceVO usdtBalance;
    private AccountBalanceVO btcBalance;

    private static final Long BUYER_ACCOUNT_ID = 100L;
    private static final Long SELLER_ACCOUNT_ID = 101L;
    private static final Long BUY_ORDER_ID = 1L;
    private static final Long SELL_ORDER_ID = 2L;
    private static final String BUY_ORDER_NO = "ORD20250518000001";
    private static final String SELL_ORDER_NO = "ORD20250518000002";

    @BeforeEach
    void setUp() {
        mockAccount = new AccountVO();
        mockAccount.setAccountId(BUYER_ACCOUNT_ID);
        mockAccount.setUserName("testuser");

        btcUsdt = new SymbolPairVO();
        btcUsdt.setSymbol("BTCUSDT");
        btcUsdt.setBaseAsset("BTC");
        btcUsdt.setQuoteAsset("USDT");
        btcUsdt.setPriceScale(2);
        btcUsdt.setQuantityScale(6);
        btcUsdt.setMinOrderAmount(new BigDecimal("10"));
        btcUsdt.setStatus(1);

        mockOrderBook = mock(OrderBook.class);

        usdtBalance = new AccountBalanceVO();
        usdtBalance.setAvailableBalance(new BigDecimal("10000"));
        usdtBalance.setFrozenBalance(new BigDecimal("500"));

        btcBalance = new AccountBalanceVO();
        btcBalance.setAvailableBalance(new BigDecimal("10"));
        btcBalance.setFrozenBalance(new BigDecimal("5"));

        // 默认 lock 直接执行 supplier
        when(orderBookManager.executeWithLock(eq("BTCUSDT"), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    /**
     * 创建买单请求的快捷方法
     */
    private CreateOrderRequestDTO createBuyRequest() {
        CreateOrderRequestDTO req = new CreateOrderRequestDTO();
        req.setAccountId(BUYER_ACCOUNT_ID);
        req.setSymbol("BTCUSDT");
        req.setSide("BUY");
        req.setOrderType("LIMIT");
        req.setPrice(new BigDecimal("50000.00"));
        req.setQuantity(new BigDecimal("0.001"));
        return req;
    }

    /**
     * 初始化数据库插入行为的 mock
     */
    private void setupOrderInsertMock() {
        doAnswer(invocation -> {
            OrderVO vo = invocation.getArgument(0);
            vo.setOrderId(BUY_ORDER_ID);
            return null;
        }).when(orderMapper).insert(any(OrderVO.class));
    }

    /**
     * 创建卖单在 updateOrderAfterTrade 中所需的 mock 返回
     */
    private OrderVO createSellOrderAfterTrade(BigDecimal filledQty, BigDecimal totalQty) {
        OrderVO sellOrder = new OrderVO();
        sellOrder.setOrderId(SELL_ORDER_ID);
        sellOrder.setOrderNo(SELL_ORDER_NO);
        sellOrder.setFilledQuantity(filledQty);
        sellOrder.setQuantity(totalQty);
        return sellOrder;
    }

    /**
     * 构建标准的 MatchResult（价格 50000，数量 0.0005，无价差）
     */
    private MatchResult createStandardMatchResult() {
        MatchResult r = new MatchResult();
        r.setTradeNo("TRD20250518000001");
        r.setSymbol("BTCUSDT");
        r.setPrice(new BigDecimal("50000.00"));
        r.setQuantity(new BigDecimal("0.0005"));
        r.setAmount(new BigDecimal("25.00"));
        r.setBuyOrderId(BUY_ORDER_ID);
        r.setSellOrderId(SELL_ORDER_ID);
        r.setBuyAccountId(BUYER_ACCOUNT_ID);
        r.setSellAccountId(SELLER_ACCOUNT_ID);
        r.setBuyOrderNo(BUY_ORDER_NO);
        r.setSellOrderNo(SELL_ORDER_NO);
        r.setBuyFee(new BigDecimal("0.025"));    // 25 * 0.1%
        r.setSellFee(new BigDecimal("0.0000005")); // 0.0005 * 0.1%
        r.setBuyFrozenAmount(new BigDecimal("25.00"));
        r.setBuyActualAmount(new BigDecimal("25.00"));
        r.setBuyRefundAmount(BigDecimal.ZERO);
        r.setSellFrozenQuantity(new BigDecimal("0.0005"));
        r.setSellActualQuantity(new BigDecimal("0.0005"));
        return r;
    }

    /**
     * 构建有价差退款的 MatchResult（买方出价 51000，卖方要价 50000）
     */
    private MatchResult createSpreadRefundMatchResult() {
        MatchResult r = createStandardMatchResult();
        // 买方冻结金额 = 51000 * 0.0005 = 25.50
        // 实际成交金额 = 50000 * 0.0005 = 25.00
        // 退款 = 25.50 - 25.00 = 0.50
        r.setBuyFrozenAmount(new BigDecimal("25.50"));
        r.setBuyRefundAmount(new BigDecimal("0.50"));
        return r;
    }

    /**
     * 设置买卖双方结算相关的 mock（标准场景：0.0005 BTC, 25 USDT）
     */
    private void setupSettlementMocks() {
        setupSettlementMocks(new BigDecimal("25.00"), new BigDecimal("0.0005"),
                new BigDecimal("0.025"), new BigDecimal("0.0000005"),
                BigDecimal.ZERO);
    }

    /**
     * 设置买卖双方结算相关的 mock（可定制参数）
     */
    private void setupSettlementMocks(BigDecimal tradeAmount, BigDecimal tradeQuantity,
                                       BigDecimal buyFee, BigDecimal sellFee,
                                       BigDecimal refundAmount) {
        // 买方 USDT 结算
        when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                .thenReturn(usdtBalance);
        when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "BTC"))
                .thenReturn(btcBalance);
        when(accountBalanceMapper.subtractFrozenBalance(BUYER_ACCOUNT_ID, "USDT", tradeAmount))
                .thenReturn(1);
        when(accountBalanceMapper.addAvailableBalance(BUYER_ACCOUNT_ID, "BTC", tradeQuantity))
                .thenReturn(1);

        // 如果有价差退款
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            when(accountBalanceMapper.addAvailableBalance(BUYER_ACCOUNT_ID, "USDT", refundAmount))
                    .thenReturn(1);
        }

        // 如果有买方手续费
        if (buyFee.compareTo(BigDecimal.ZERO) > 0) {
            when(accountBalanceMapper.subtractAvailableBalance(BUYER_ACCOUNT_ID, "USDT", buyFee))
                    .thenReturn(1);
        }

        // 卖方 BTC 结算
        when(accountBalanceMapper.selectByAccountIdAndAsset(SELLER_ACCOUNT_ID, "BTC"))
                .thenReturn(btcBalance);
        when(accountBalanceMapper.selectByAccountIdAndAsset(SELLER_ACCOUNT_ID, "USDT"))
                .thenReturn(usdtBalance);
        when(accountBalanceMapper.subtractFrozenBalance(SELLER_ACCOUNT_ID, "BTC", tradeQuantity))
                .thenReturn(1);
        when(accountBalanceMapper.addAvailableBalance(SELLER_ACCOUNT_ID, "USDT", tradeAmount))
                .thenReturn(1);

        // 如果有卖方手续费
        if (sellFee.compareTo(BigDecimal.ZERO) > 0) {
            when(accountBalanceMapper.subtractAvailableBalance(SELLER_ACCOUNT_ID, "BTC", sellFee))
                    .thenReturn(1);
        }
    }

    // ==================== 普通成交结算 ====================

    @Nested
    @DisplayName("基础成交结算")
    class BasicSettlement {

        @Test
        @DisplayName("买单撮合成功：扣除USDT冻结 + 增加BTC余额 + 扣除手续费 + 无价差退款")
        void testBuySettlement_NoRefund() {
            setupOrderInsertMock();
            when(accountMapper.getAccountById(BUYER_ACCOUNT_ID)).thenReturn(mockAccount);
            when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcUsdt);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.freezeBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("50.00")))
                    .thenReturn(1);

            OrderVO createdOrder = new OrderVO();
            createdOrder.setOrderId(BUY_ORDER_ID);
            createdOrder.setOrderNo(BUY_ORDER_NO);
            createdOrder.setSymbol("BTCUSDT");
            createdOrder.setSide("BUY");
            createdOrder.setOrderType("LIMIT");
            createdOrder.setPrice(new BigDecimal("50000.00"));
            createdOrder.setQuantity(new BigDecimal("0.001"));
            createdOrder.setFilledQuantity(BigDecimal.ZERO);
            createdOrder.setStatus("NEW");

            OrderVO filledOrder = new OrderVO();
            filledOrder.setOrderId(BUY_ORDER_ID);
            filledOrder.setOrderNo(BUY_ORDER_NO);
            filledOrder.setStatus("PARTIALLY_FILLED");
            filledOrder.setFilledQuantity(new BigDecimal("0.0005"));
            filledOrder.setQuantity(new BigDecimal("0.001"));

            when(orderMapper.selectById(BUY_ORDER_ID))
                    .thenReturn(createdOrder, filledOrder, filledOrder);
            when(orderMapper.incrementFilledQuantity(anyLong(), any(BigDecimal.class))).thenReturn(1);

            when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
            when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook)))
                    .thenReturn(List.of(createStandardMatchResult()));

            // 卖单查询（updateOrderAfterTrade 中需要，filled == total 表示完全成交）
            when(orderMapper.selectById(SELL_ORDER_ID))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.0005"), new BigDecimal("0.0005")));

            setupSettlementMocks();

            OrderVO result = orderService.createOrder(createBuyRequest());

            assertNotNull(result);

            // 验证成交记录插入
            verify(tradeMapper).insert(any());

            // 验证买方结算
            verify(accountBalanceMapper).subtractFrozenBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("25.00"));
            verify(accountBalanceMapper).addAvailableBalance(BUYER_ACCOUNT_ID, "BTC", new BigDecimal("0.0005"));
            verify(accountBalanceMapper).subtractAvailableBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("0.025"));

            // 验证卖方结算
            verify(accountBalanceMapper).subtractFrozenBalance(SELLER_ACCOUNT_ID, "BTC", new BigDecimal("0.0005"));
            verify(accountBalanceMapper).addAvailableBalance(SELLER_ACCOUNT_ID, "USDT", new BigDecimal("25.00"));
            verify(accountBalanceMapper).subtractAvailableBalance(SELLER_ACCOUNT_ID, "BTC", new BigDecimal("0.0000005"));

            // 验证订单状态更新
            verify(orderMapper).incrementFilledQuantity(eq(BUY_ORDER_ID), any(BigDecimal.class));
            verify(orderMapper).incrementFilledQuantity(eq(SELL_ORDER_ID), any(BigDecimal.class));

            // 验证插入了资产流水记录（至少 4 条：冻结、买方支付、买方收款、卖方、手续费等）
            verify(assetLedgerMapper, atLeast(4)).insert(any(AssetLedgerRecord.class));
        }
    }

    // ==================== 价差退款场景 ====================

    @Nested
    @DisplayName("价差退款")
    class SpreadRefund {

        @Test
        @DisplayName("买方出价高于卖方要价：产生价差退款")
        void testBuySettlement_WithSpreadRefund() {
            setupOrderInsertMock();
            when(accountMapper.getAccountById(BUYER_ACCOUNT_ID)).thenReturn(mockAccount);
            when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcUsdt);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.freezeBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("50.00")))
                    .thenReturn(1);

            OrderVO createdOrder = new OrderVO();
            createdOrder.setOrderId(BUY_ORDER_ID);
            createdOrder.setOrderNo(BUY_ORDER_NO);
            createdOrder.setStatus("NEW");
            createdOrder.setFilledQuantity(BigDecimal.ZERO);
            createdOrder.setQuantity(new BigDecimal("0.001"));

            OrderVO filledOrder = new OrderVO();
            filledOrder.setOrderId(BUY_ORDER_ID);
            filledOrder.setOrderNo(BUY_ORDER_NO);
            filledOrder.setStatus("PARTIALLY_FILLED");
            filledOrder.setFilledQuantity(new BigDecimal("0.0005"));
            filledOrder.setQuantity(new BigDecimal("0.001"));

            when(orderMapper.selectById(BUY_ORDER_ID))
                    .thenReturn(createdOrder, filledOrder, filledOrder);
            when(orderMapper.incrementFilledQuantity(anyLong(), any(BigDecimal.class))).thenReturn(1);

            when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
            when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook)))
                    .thenReturn(List.of(createSpreadRefundMatchResult()));

            // 卖单查询
            when(orderMapper.selectById(SELL_ORDER_ID))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.0005"), new BigDecimal("0.0005")));

            setupSettlementMocks(new BigDecimal("25.00"), new BigDecimal("0.0005"),
                    new BigDecimal("0.025"), new BigDecimal("0.0000005"),
                    new BigDecimal("0.50"));

            orderService.createOrder(createBuyRequest());

            // 验证价差退款被调用
            verify(accountBalanceMapper).addAvailableBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("0.50"));

            // 验证 TRADE_REFUND 流水
            verify(assetLedgerMapper, atLeast(1)).insert(ledgerCaptor.capture());
            boolean hasRefundLedger = ledgerCaptor.getAllValues().stream()
                    .anyMatch(l -> "TRADE_REFUND".equals(l.getBusinessType()));
            assertTrue(hasRefundLedger, "应该产生价差退款的资产流水记录");
        }

        @Test
        @DisplayName("零价差退款：不产生退款流水")
        void testBuySettlement_ZeroRefund_NoRefundLedger() {
            setupOrderInsertMock();
            when(accountMapper.getAccountById(BUYER_ACCOUNT_ID)).thenReturn(mockAccount);
            when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcUsdt);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.freezeBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("50.00")))
                    .thenReturn(1);

            OrderVO createdOrder = new OrderVO();
            createdOrder.setOrderId(BUY_ORDER_ID);
            createdOrder.setOrderNo(BUY_ORDER_NO);
            createdOrder.setStatus("NEW");
            createdOrder.setFilledQuantity(BigDecimal.ZERO);
            createdOrder.setQuantity(new BigDecimal("0.001"));

            OrderVO filledOrder = new OrderVO();
            filledOrder.setOrderId(BUY_ORDER_ID);
            filledOrder.setOrderNo(BUY_ORDER_NO);
            filledOrder.setStatus("PARTIALLY_FILLED");
            filledOrder.setFilledQuantity(new BigDecimal("0.0005"));
            filledOrder.setQuantity(new BigDecimal("0.001"));

            when(orderMapper.selectById(BUY_ORDER_ID))
                    .thenReturn(createdOrder, filledOrder, filledOrder);
            when(orderMapper.incrementFilledQuantity(anyLong(), any(BigDecimal.class))).thenReturn(1);

            when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
            when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook)))
                    .thenReturn(List.of(createStandardMatchResult()));

            // 卖单查询
            when(orderMapper.selectById(SELL_ORDER_ID))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.0005"), new BigDecimal("0.0005")));

            setupSettlementMocks();

            orderService.createOrder(createBuyRequest());

            // 验证没有价差退款调用（addAvailableBalance 仅对 BTC 调用，不对 USDT 额外调用）
            verify(accountBalanceMapper, never())
                    .addAvailableBalance(eq(BUYER_ACCOUNT_ID), eq("USDT"), any(BigDecimal.class));

            // 验证没有 TRADE_REFUND 流水
            verify(assetLedgerMapper, atLeast(1)).insert(ledgerCaptor.capture());
            boolean hasRefundLedger = ledgerCaptor.getAllValues().stream()
                    .anyMatch(l -> "TRADE_REFUND".equals(l.getBusinessType()));
            assertFalse(hasRefundLedger, "零价差退款时不应该产生 TRADE_REFUND 流水");
        }
    }

    // ==================== 零手续费场景 ====================

    @Nested
    @DisplayName("手续费边界")
    class FeeEdgeCases {

        @Test
        @DisplayName("买方零手续费：不产生 FEE 流水")
        void testBuySettlement_ZeroBuyFee() {
            setupOrderInsertMock();
            when(accountMapper.getAccountById(BUYER_ACCOUNT_ID)).thenReturn(mockAccount);
            when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcUsdt);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.freezeBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("50.00")))
                    .thenReturn(1);

            OrderVO createdOrder = new OrderVO();
            createdOrder.setOrderId(BUY_ORDER_ID);
            createdOrder.setOrderNo(BUY_ORDER_NO);
            createdOrder.setStatus("NEW");
            createdOrder.setFilledQuantity(BigDecimal.ZERO);
            createdOrder.setQuantity(new BigDecimal("0.001"));

            OrderVO filledOrder = new OrderVO();
            filledOrder.setOrderId(BUY_ORDER_ID);
            filledOrder.setOrderNo(BUY_ORDER_NO);
            filledOrder.setStatus("PARTIALLY_FILLED");
            filledOrder.setFilledQuantity(new BigDecimal("0.0005"));
            filledOrder.setQuantity(new BigDecimal("0.001"));

            when(orderMapper.selectById(BUY_ORDER_ID))
                    .thenReturn(createdOrder, filledOrder, filledOrder);
            when(orderMapper.incrementFilledQuantity(anyLong(), any(BigDecimal.class))).thenReturn(1);

            when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
            MatchResult zeroFeeResult = createStandardMatchResult();
            zeroFeeResult.setBuyFee(BigDecimal.ZERO);
            zeroFeeResult.setSellFee(BigDecimal.ZERO);
            when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook)))
                    .thenReturn(List.of(zeroFeeResult));

            // 卖单查询
            when(orderMapper.selectById(SELL_ORDER_ID))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.0005"), new BigDecimal("0.0005")));

            setupSettlementMocks(new BigDecimal("25.00"), new BigDecimal("0.0005"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

            orderService.createOrder(createBuyRequest());

            // 验证没有手续费扣减调用
            verify(accountBalanceMapper, never())
                    .subtractAvailableBalance(eq(BUYER_ACCOUNT_ID), eq("USDT"), any(BigDecimal.class));

            // 验证没有 FEE 类型的流水
            verify(assetLedgerMapper, atLeast(1)).insert(ledgerCaptor.capture());
            boolean hasFeeLedger = ledgerCaptor.getAllValues().stream()
                    .anyMatch(l -> "FEE".equals(l.getBusinessType()));
            assertFalse(hasFeeLedger, "零手续费时不应该产生 FEE 流水");
        }
    }

    // ==================== 多笔成交（部分填满） ====================

    @Nested
    @DisplayName("多笔成交")
    class MultipleMatches {

        @Test
        @DisplayName("买入订单跨多个卖方成交：多次结算")
        void testMultipleMatchResults_ForBuyOrder() {
            setupOrderInsertMock();
            when(accountMapper.getAccountById(BUYER_ACCOUNT_ID)).thenReturn(mockAccount);
            when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcUsdt);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.freezeBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("50.00")))
                    .thenReturn(1);

            OrderVO createdOrder = new OrderVO();
            createdOrder.setOrderId(BUY_ORDER_ID);
            createdOrder.setOrderNo(BUY_ORDER_NO);
            createdOrder.setStatus("NEW");
            createdOrder.setFilledQuantity(BigDecimal.ZERO);
            createdOrder.setQuantity(new BigDecimal("0.001"));

            // 第1笔成交后：filled=0.0004, PARTIALLY_FILLED
            OrderVO afterFirstMatch = new OrderVO();
            afterFirstMatch.setOrderId(BUY_ORDER_ID);
            afterFirstMatch.setOrderNo(BUY_ORDER_NO);
            afterFirstMatch.setStatus("PARTIALLY_FILLED");
            afterFirstMatch.setFilledQuantity(new BigDecimal("0.0004"));
            afterFirstMatch.setQuantity(new BigDecimal("0.001"));

            // 第2笔成交后：filled=0.001, FILLED
            OrderVO afterSecondMatch = new OrderVO();
            afterSecondMatch.setOrderId(BUY_ORDER_ID);
            afterSecondMatch.setOrderNo(BUY_ORDER_NO);
            afterSecondMatch.setStatus("FILLED");
            afterSecondMatch.setFilledQuantity(new BigDecimal("0.001"));
            afterSecondMatch.setQuantity(new BigDecimal("0.001"));

            when(orderMapper.selectById(BUY_ORDER_ID))
                    .thenReturn(createdOrder, afterFirstMatch, afterSecondMatch);
            when(orderMapper.incrementFilledQuantity(anyLong(), any(BigDecimal.class))).thenReturn(1);

            when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);

            // 两笔成交结果
            MatchResult match1 = new MatchResult();
            match1.setTradeNo("TRD0001");
            match1.setSymbol("BTCUSDT");
            match1.setPrice(new BigDecimal("50000.00"));
            match1.setQuantity(new BigDecimal("0.0004"));
            match1.setAmount(new BigDecimal("20.00"));
            match1.setBuyOrderId(1L);
            match1.setSellOrderId(3L);
            match1.setBuyAccountId(BUYER_ACCOUNT_ID);
            match1.setSellAccountId(102L);
            match1.setBuyOrderNo(BUY_ORDER_NO);
            match1.setSellOrderNo("ORD_SELL_001");
            match1.setBuyFee(new BigDecimal("0.02"));
            match1.setSellFee(new BigDecimal("0.0000004"));
            match1.setBuyFrozenAmount(new BigDecimal("20.00"));
            match1.setBuyActualAmount(new BigDecimal("20.00"));
            match1.setBuyRefundAmount(BigDecimal.ZERO);
            match1.setSellFrozenQuantity(new BigDecimal("0.0004"));
            match1.setSellActualQuantity(new BigDecimal("0.0004"));

            MatchResult match2 = new MatchResult();
            match2.setTradeNo("TRD0002");
            match2.setSymbol("BTCUSDT");
            match2.setPrice(new BigDecimal("50000.00"));
            match2.setQuantity(new BigDecimal("0.0006"));
            match2.setAmount(new BigDecimal("30.00"));
            match2.setBuyOrderId(1L);
            match2.setSellOrderId(4L);
            match2.setBuyAccountId(BUYER_ACCOUNT_ID);
            match2.setSellAccountId(103L);
            match2.setBuyOrderNo(BUY_ORDER_NO);
            match2.setSellOrderNo("ORD_SELL_002");
            match2.setBuyFee(new BigDecimal("0.03"));
            match2.setSellFee(new BigDecimal("0.0000006"));
            match2.setBuyFrozenAmount(new BigDecimal("30.00"));
            match2.setBuyActualAmount(new BigDecimal("30.00"));
            match2.setBuyRefundAmount(BigDecimal.ZERO);
            match2.setSellFrozenQuantity(new BigDecimal("0.0006"));
            match2.setSellActualQuantity(new BigDecimal("0.0006"));

            when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook)))
                    .thenReturn(List.of(match1, match2));

            // 买方结算（两笔成交）
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "BTC"))
                    .thenReturn(btcBalance);
            when(accountBalanceMapper.subtractFrozenBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("20.00")))
                    .thenReturn(1);
            when(accountBalanceMapper.subtractFrozenBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("30.00")))
                    .thenReturn(1);
            when(accountBalanceMapper.addAvailableBalance(BUYER_ACCOUNT_ID, "BTC", new BigDecimal("0.0004")))
                    .thenReturn(1);
            when(accountBalanceMapper.addAvailableBalance(BUYER_ACCOUNT_ID, "BTC", new BigDecimal("0.0006")))
                    .thenReturn(1);
            when(accountBalanceMapper.subtractAvailableBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("0.02")))
                    .thenReturn(1);
            when(accountBalanceMapper.subtractAvailableBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("0.03")))
                    .thenReturn(1);

            // 卖方结算 (seller 1: 102, seller 2: 103)
            AccountBalanceVO sellerBtcBalance = new AccountBalanceVO();
            sellerBtcBalance.setAvailableBalance(new BigDecimal("10"));
            sellerBtcBalance.setFrozenBalance(new BigDecimal("5"));
            AccountBalanceVO sellerUsdtBalance = new AccountBalanceVO();
            sellerUsdtBalance.setAvailableBalance(new BigDecimal("10000"));
            sellerUsdtBalance.setFrozenBalance(new BigDecimal("500"));

            when(accountBalanceMapper.selectByAccountIdAndAsset(102L, "BTC")).thenReturn(sellerBtcBalance);
            when(accountBalanceMapper.selectByAccountIdAndAsset(102L, "USDT")).thenReturn(sellerUsdtBalance);
            when(accountBalanceMapper.selectByAccountIdAndAsset(103L, "BTC")).thenReturn(sellerBtcBalance);
            when(accountBalanceMapper.selectByAccountIdAndAsset(103L, "USDT")).thenReturn(sellerUsdtBalance);
            when(accountBalanceMapper.subtractFrozenBalance(102L, "BTC", new BigDecimal("0.0004"))).thenReturn(1);
            when(accountBalanceMapper.subtractFrozenBalance(103L, "BTC", new BigDecimal("0.0006"))).thenReturn(1);
            when(accountBalanceMapper.addAvailableBalance(102L, "USDT", new BigDecimal("20.00"))).thenReturn(1);
            when(accountBalanceMapper.addAvailableBalance(103L, "USDT", new BigDecimal("30.00"))).thenReturn(1);
            when(accountBalanceMapper.subtractAvailableBalance(102L, "BTC", new BigDecimal("0.0000004"))).thenReturn(1);
            when(accountBalanceMapper.subtractAvailableBalance(103L, "BTC", new BigDecimal("0.0000006"))).thenReturn(1);

            // 卖单 1 查询（updateOrderAfterTrade）
            when(orderMapper.selectById(3L))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.0004"), new BigDecimal("0.0004")));
            // 卖单 2 查询
            when(orderMapper.selectById(4L))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.0006"), new BigDecimal("0.0006")));

            orderService.createOrder(createBuyRequest());

            // 验证两笔成交记录都被插入
            verify(tradeMapper, times(2)).insert(any());

            // 验证买方结算被调用了两次（每次成交一次）
            verify(accountBalanceMapper, times(2))
                    .subtractFrozenBalance(eq(BUYER_ACCOUNT_ID), eq("USDT"), any(BigDecimal.class));

            // 验证订单增量更新被调用了4次（2笔成交 × 买方+卖方）
            verify(orderMapper, times(4)).incrementFilledQuantity(anyLong(), any(BigDecimal.class));
        }
    }

    // ==================== 订单状态更新 ====================

    @Nested
    @DisplayName("订单状态更新")
    class OrderStatusUpdate {

        @Test
        @DisplayName("部分成交：状态变为 PARTIALLY_FILLED")
        void testPartiallyFilledStatus() {
            setupOrderInsertMock();
            when(accountMapper.getAccountById(BUYER_ACCOUNT_ID)).thenReturn(mockAccount);
            when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcUsdt);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.freezeBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("50.00")))
                    .thenReturn(1);

            OrderVO createdOrder = new OrderVO();
            createdOrder.setOrderId(BUY_ORDER_ID);
            createdOrder.setOrderNo(BUY_ORDER_NO);
            createdOrder.setStatus("NEW");
            createdOrder.setFilledQuantity(BigDecimal.ZERO);
            createdOrder.setQuantity(new BigDecimal("0.001"));

            // 只成交 0.0003，剩余 0.0007 → PARTIALLY_FILLED
            OrderVO partialOrder = new OrderVO();
            partialOrder.setOrderId(BUY_ORDER_ID);
            partialOrder.setOrderNo(BUY_ORDER_NO);
            partialOrder.setStatus("PARTIALLY_FILLED");
            partialOrder.setFilledQuantity(new BigDecimal("0.0003"));
            partialOrder.setQuantity(new BigDecimal("0.001"));

            when(orderMapper.selectById(BUY_ORDER_ID))
                    .thenReturn(createdOrder, partialOrder, partialOrder);
            when(orderMapper.incrementFilledQuantity(anyLong(), any(BigDecimal.class))).thenReturn(1);

            when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
            MatchResult partialMatch = createStandardMatchResult();
            partialMatch.setQuantity(new BigDecimal("0.0003"));
            partialMatch.setAmount(new BigDecimal("15.00"));
            partialMatch.setBuyFrozenAmount(new BigDecimal("15.00"));
            partialMatch.setBuyFee(new BigDecimal("0.015"));
            partialMatch.setSellFrozenQuantity(new BigDecimal("0.0003"));
            partialMatch.setSellFee(new BigDecimal("0.0000003"));
            when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook)))
                    .thenReturn(List.of(partialMatch));

            // 卖单查询（完全成交，但卖方的 filled == total）
            when(orderMapper.selectById(SELL_ORDER_ID))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.0003"), new BigDecimal("0.0003")));

            setupSettlementMocks(new BigDecimal("15.00"), new BigDecimal("0.0003"),
                    new BigDecimal("0.015"), new BigDecimal("0.0000003"),
                    BigDecimal.ZERO);

            orderService.createOrder(createBuyRequest());

            // 验证状态更新为 PARTIALLY_FILLED
            verify(orderMapper).updateStatus(BUY_ORDER_ID, "PARTIALLY_FILLED");
        }

        @Test
        @DisplayName("完全成交：状态变为 FILLED")
        void testFullyFilledStatus() {
            setupOrderInsertMock();
            when(accountMapper.getAccountById(BUYER_ACCOUNT_ID)).thenReturn(mockAccount);
            when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcUsdt);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.freezeBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("50.00")))
                    .thenReturn(1);

            OrderVO createdOrder = new OrderVO();
            createdOrder.setOrderId(BUY_ORDER_ID);
            createdOrder.setOrderNo(BUY_ORDER_NO);
            createdOrder.setStatus("NEW");
            createdOrder.setFilledQuantity(BigDecimal.ZERO);
            createdOrder.setQuantity(new BigDecimal("0.001"));

            // 完全成交 0.001 → FILLED
            OrderVO filledOrder = new OrderVO();
            filledOrder.setOrderId(BUY_ORDER_ID);
            filledOrder.setOrderNo(BUY_ORDER_NO);
            filledOrder.setStatus("FILLED");
            filledOrder.setFilledQuantity(new BigDecimal("0.001"));
            filledOrder.setQuantity(new BigDecimal("0.001"));

            when(orderMapper.selectById(BUY_ORDER_ID))
                    .thenReturn(createdOrder, filledOrder, filledOrder);
            when(orderMapper.incrementFilledQuantity(anyLong(), any(BigDecimal.class))).thenReturn(1);

            when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
            MatchResult fullMatch = createStandardMatchResult();
            fullMatch.setQuantity(new BigDecimal("0.001"));
            fullMatch.setAmount(new BigDecimal("50.00"));
            fullMatch.setBuyFrozenAmount(new BigDecimal("50.00"));
            fullMatch.setBuyFee(new BigDecimal("0.05"));
            fullMatch.setSellFrozenQuantity(new BigDecimal("0.001"));
            fullMatch.setSellFee(new BigDecimal("0.000001"));
            when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook)))
                    .thenReturn(List.of(fullMatch));

            // 卖单查询（完全成交）
            when(orderMapper.selectById(SELL_ORDER_ID))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.001"), new BigDecimal("0.001")));

            setupSettlementMocks(new BigDecimal("50.00"), new BigDecimal("0.001"),
                    new BigDecimal("0.05"), new BigDecimal("0.000001"),
                    BigDecimal.ZERO);

            orderService.createOrder(createBuyRequest());

            // 验证状态更新为 FILLED
            verify(orderMapper).updateStatus(BUY_ORDER_ID, "FILLED");
        }
    }

    // ==================== 余额边界场景 ====================

    @Nested
    @DisplayName("余额边界")
    class BalanceEdgeCases {

        @Test
        @DisplayName("买方没有 BTC 余额记录：自动创建")
        void testBuyerHasNoBtcBalance_InsertNewBalance() {
            setupOrderInsertMock();
            when(accountMapper.getAccountById(BUYER_ACCOUNT_ID)).thenReturn(mockAccount);
            when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcUsdt);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.freezeBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("50.00")))
                    .thenReturn(1);

            OrderVO createdOrder = new OrderVO();
            createdOrder.setOrderId(BUY_ORDER_ID);
            createdOrder.setOrderNo(BUY_ORDER_NO);
            createdOrder.setStatus("NEW");
            createdOrder.setFilledQuantity(BigDecimal.ZERO);
            createdOrder.setQuantity(new BigDecimal("0.001"));

            OrderVO partialOrder = new OrderVO();
            partialOrder.setOrderId(BUY_ORDER_ID);
            partialOrder.setOrderNo(BUY_ORDER_NO);
            partialOrder.setStatus("PARTIALLY_FILLED");
            partialOrder.setFilledQuantity(new BigDecimal("0.0005"));
            partialOrder.setQuantity(new BigDecimal("0.001"));

            when(orderMapper.selectById(BUY_ORDER_ID))
                    .thenReturn(createdOrder, partialOrder, partialOrder);
            when(orderMapper.incrementFilledQuantity(anyLong(), any(BigDecimal.class))).thenReturn(1);

            when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
            when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook)))
                    .thenReturn(List.of(createStandardMatchResult()));

            // 买方 USDT 余额存在，BTC 余额为 null（首次无 BTC 余额）
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "BTC"))
                    .thenReturn(null);
            when(accountBalanceMapper.subtractFrozenBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("25.00")))
                    .thenReturn(1);
            when(accountBalanceMapper.addAvailableBalance(BUYER_ACCOUNT_ID, "BTC", new BigDecimal("0.0005")))
                    .thenReturn(1);
            when(accountBalanceMapper.subtractAvailableBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("0.025")))
                    .thenReturn(1);

            // 卖方结算
            when(accountBalanceMapper.selectByAccountIdAndAsset(SELLER_ACCOUNT_ID, "BTC"))
                    .thenReturn(btcBalance);
            when(accountBalanceMapper.selectByAccountIdAndAsset(SELLER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.subtractFrozenBalance(SELLER_ACCOUNT_ID, "BTC", new BigDecimal("0.0005")))
                    .thenReturn(1);
            when(accountBalanceMapper.addAvailableBalance(SELLER_ACCOUNT_ID, "USDT", new BigDecimal("25.00")))
                    .thenReturn(1);
            when(accountBalanceMapper.subtractAvailableBalance(SELLER_ACCOUNT_ID, "BTC", new BigDecimal("0.0000005")))
                    .thenReturn(1);

            // 卖单查询
            when(orderMapper.selectById(SELL_ORDER_ID))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.0005"), new BigDecimal("0.0005")));

            orderService.createOrder(createBuyRequest());

            // 验证自动创建了 BTC 余额记录
            verify(accountBalanceMapper).insertBalance(BUYER_ACCOUNT_ID, "BTC");
        }

        @Test
        @DisplayName("卖方没有 USDT 余额记录：自动创建")
        void testSellerHasNoUsdtBalance_InsertNewBalance() {
            setupOrderInsertMock();
            when(accountMapper.getAccountById(BUYER_ACCOUNT_ID)).thenReturn(mockAccount);
            when(symbolPairMapper.selectBySymbol("BTCUSDT")).thenReturn(btcUsdt);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.freezeBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("50.00")))
                    .thenReturn(1);

            OrderVO createdOrder = new OrderVO();
            createdOrder.setOrderId(BUY_ORDER_ID);
            createdOrder.setOrderNo(BUY_ORDER_NO);
            createdOrder.setStatus("NEW");
            createdOrder.setFilledQuantity(BigDecimal.ZERO);
            createdOrder.setQuantity(new BigDecimal("0.001"));

            OrderVO partialOrder = new OrderVO();
            partialOrder.setOrderId(BUY_ORDER_ID);
            partialOrder.setOrderNo(BUY_ORDER_NO);
            partialOrder.setStatus("PARTIALLY_FILLED");
            partialOrder.setFilledQuantity(new BigDecimal("0.0005"));
            partialOrder.setQuantity(new BigDecimal("0.001"));

            when(orderMapper.selectById(BUY_ORDER_ID))
                    .thenReturn(createdOrder, partialOrder, partialOrder);
            when(orderMapper.incrementFilledQuantity(anyLong(), any(BigDecimal.class))).thenReturn(1);

            when(orderBookManager.getOrderBook("BTCUSDT")).thenReturn(mockOrderBook);
            when(matchEngine.match(any(OrderVO.class), eq(mockOrderBook)))
                    .thenReturn(List.of(createStandardMatchResult()));

            // 买方结算
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "USDT"))
                    .thenReturn(usdtBalance);
            when(accountBalanceMapper.selectByAccountIdAndAsset(BUYER_ACCOUNT_ID, "BTC"))
                    .thenReturn(btcBalance);
            when(accountBalanceMapper.subtractFrozenBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("25.00")))
                    .thenReturn(1);
            when(accountBalanceMapper.addAvailableBalance(BUYER_ACCOUNT_ID, "BTC", new BigDecimal("0.0005")))
                    .thenReturn(1);
            when(accountBalanceMapper.subtractAvailableBalance(BUYER_ACCOUNT_ID, "USDT", new BigDecimal("0.025")))
                    .thenReturn(1);

            // 卖方 BTC 存在，但 USDT 为 null
            when(accountBalanceMapper.selectByAccountIdAndAsset(SELLER_ACCOUNT_ID, "BTC"))
                    .thenReturn(btcBalance);
            when(accountBalanceMapper.selectByAccountIdAndAsset(SELLER_ACCOUNT_ID, "USDT"))
                    .thenReturn(null);
            when(accountBalanceMapper.subtractFrozenBalance(SELLER_ACCOUNT_ID, "BTC", new BigDecimal("0.0005")))
                    .thenReturn(1);
            when(accountBalanceMapper.addAvailableBalance(SELLER_ACCOUNT_ID, "USDT", new BigDecimal("25.00")))
                    .thenReturn(1);
            when(accountBalanceMapper.subtractAvailableBalance(SELLER_ACCOUNT_ID, "BTC", new BigDecimal("0.0000005")))
                    .thenReturn(1);

            // 卖单查询
            when(orderMapper.selectById(SELL_ORDER_ID))
                    .thenReturn(createSellOrderAfterTrade(new BigDecimal("0.0005"), new BigDecimal("0.0005")));

            orderService.createOrder(createBuyRequest());

            // 验证自动创建了卖方 USDT 余额记录
            verify(accountBalanceMapper).insertBalance(SELLER_ACCOUNT_ID, "USDT");
        }
    }
}
