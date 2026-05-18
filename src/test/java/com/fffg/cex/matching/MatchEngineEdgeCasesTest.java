package com.fffg.cex.matching;

import com.fffg.cex.order.vo.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 撮合引擎边缘用例测试
 * <p>
 * 覆盖 {@link MatchEngineTest} 未覆盖的场景：同价交叉、极小数量、手续费舍入、多档位成交、退单等。
 */
class MatchEngineEdgeCasesTest {

    private MatchEngine matchEngine;
    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        matchEngine = new MatchEngine();
        orderBook = new OrderBook();
    }

    // ==================== 同价交叉 ====================

    @Test
    void testSamePriceCrossing_BuyTaker() {
        // maker 卖单 50000，taker 买单也是 50000 → 应该成交
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(1, results.size());
        assertEquals(new BigDecimal("50000"), results.get(0).getPrice());
        assertEquals(new BigDecimal("0.1"), results.get(0).getQuantity());
    }

    @Test
    void testSamePriceCrossing_SellTaker() {
        // maker 买单 50000，taker 卖单也是 50000 → 应该成交
        OrderVO buyOrder = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(buyOrder);

        OrderVO sellOrder = createOrder(2L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(sellOrder, orderBook);

        assertEquals(1, results.size());
        assertEquals(new BigDecimal("50000"), results.get(0).getPrice());
    }

    // ==================== 极小数量 / 手续费舍入 ====================

    @Test
    void testVerySmallQuantity_FeeRounding() {
        // 极小数量交易，验证手续费舍入
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.000001"));
        orderBook.addOrder(sellOrder);

        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("0.000001"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(1, results.size());
        MatchResult result = results.get(0);

        // 成交金额 = 50000 * 0.000001 = 0.05
        assertEquals(0, new BigDecimal("0.05").compareTo(result.getAmount()));

        // 买方手续费 = 0.05 * 0.001 = 0.00005，scale=8 → 0.00005000
        assertEquals(0, new BigDecimal("0.00005000").compareTo(result.getBuyFee()));

        // 卖方手续费 = 0.000001 * 0.001 = 0.000000001，scale=8 → 0.00000000 (DOWN舍入)
        assertEquals(0, new BigDecimal("0.00000000").compareTo(result.getSellFee()));
    }

    @Test
    void testExtremelySmallQuantity_NoOverflow() {
        // 测试极小数量不会导致数值溢出
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("0.00000001"), new BigDecimal("0.00000001"));
        orderBook.addOrder(sellOrder);

        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("0.00000002"), new BigDecimal("0.00000001"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(1, results.size());
        MatchResult result = results.get(0);
        assertTrue(result.getAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    // ==================== 多档位成交 ====================

    @Test
    void testMultiLevelFill_TakerBuyerAcrossPriceLevels() {
        // 三个不同价格的卖单
        OrderVO sell1 = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        OrderVO sell2 = createOrder(2L, "SELL", new BigDecimal("50100"), new BigDecimal("0.2"));
        OrderVO sell3 = createOrder(3L, "SELL", new BigDecimal("50200"), new BigDecimal("0.3"));
        orderBook.addOrder(sell1);
        orderBook.addOrder(sell2);
        orderBook.addOrder(sell3);

        // 大买单，可以吃掉所有三个档位
        OrderVO buyOrder = createOrder(4L, "BUY", new BigDecimal("51000"), new BigDecimal("0.6"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(3, results.size());

        // 验证价格优先：先成交最低价
        assertEquals(new BigDecimal("50000"), results.get(0).getPrice());
        assertEquals(new BigDecimal("0.1"), results.get(0).getQuantity());

        assertEquals(new BigDecimal("50100"), results.get(1).getPrice());
        assertEquals(new BigDecimal("0.2"), results.get(1).getQuantity());

        assertEquals(new BigDecimal("50200"), results.get(2).getPrice());
        assertEquals(new BigDecimal("0.3"), results.get(2).getQuantity());

        // taker 完全成交
        assertEquals(0, new BigDecimal("0.6").compareTo(buyOrder.getFilledQuantity()));
    }

    @Test
    void testMultiLevelFill_TakerSellerAcrossPriceLevels() {
        // 三个不同价格的买单
        OrderVO buy1 = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        OrderVO buy2 = createOrder(2L, "BUY", new BigDecimal("49900"), new BigDecimal("0.2"));
        OrderVO buy3 = createOrder(3L, "BUY", new BigDecimal("49800"), new BigDecimal("0.3"));
        orderBook.addOrder(buy1);
        orderBook.addOrder(buy2);
        orderBook.addOrder(buy3);

        // 大卖单，可以吃掉所有三个档位
        OrderVO sellOrder = createOrder(4L, "SELL", new BigDecimal("49000"), new BigDecimal("0.6"));
        List<MatchResult> results = matchEngine.match(sellOrder, orderBook);

        assertEquals(3, results.size());

        // 验证价格优先：先成交最高价
        assertEquals(new BigDecimal("50000"), results.get(0).getPrice());
        assertEquals(new BigDecimal("0.1"), results.get(0).getQuantity());

        assertEquals(new BigDecimal("49900"), results.get(1).getPrice());
        assertEquals(new BigDecimal("0.2"), results.get(1).getQuantity());

        assertEquals(new BigDecimal("49800"), results.get(2).getPrice());
        assertEquals(new BigDecimal("0.3"), results.get(2).getQuantity());
    }

    // ==================== 买方价差退款 (Buy Refund) ====================

    @Test
    void testBuyRefund_WhenTakerIsBuyer() {
        // maker 卖单 50000
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        // taker 买单价格更高 51000 → 应产生价差退款
        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(1, results.size());
        MatchResult result = results.get(0);

        // 买方冻结金额 = taker价格 * 数量 = 51000 * 0.1 = 5100
        assertEquals(0, new BigDecimal("5100").compareTo(result.getBuyFrozenAmount()));

        // 买方实际成交金额 = maker价格 * 数量 = 50000 * 0.1 = 5000
        assertEquals(0, new BigDecimal("5000").compareTo(result.getBuyActualAmount()));

        // 退款 = 5100 - 5000 = 100
        assertEquals(0, new BigDecimal("100").compareTo(result.getBuyRefundAmount()));
    }

    @Test
    void testBuyRefund_WhenMakerIsBuyer_TakerIsSeller() {
        // maker 买单 50000
        OrderVO buyOrder = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(buyOrder);

        // taker 卖单价格更低 49000 → 应产生价差退款（买方是 maker）
        OrderVO sellOrder = createOrder(2L, "SELL", new BigDecimal("49000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(sellOrder, orderBook);

        assertEquals(1, results.size());
        MatchResult result = results.get(0);

        // 此时买方是 maker（order1），买方冻结金额 = maker价格 * 数量
        assertEquals(0, new BigDecimal("5000").compareTo(result.getBuyFrozenAmount()));
        // 买方实际成交金额 = maker价格 * 数量 = 5000（成交价以 maker 为准）
        assertEquals(0, new BigDecimal("5000").compareTo(result.getBuyActualAmount()));
        // 没有退款，因为 maker 价格就是成交价
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getBuyRefundAmount()));
    }

    // ==================== taker 部分成交后留在订单簿 ====================

    @Test
    void testTakerPartiallyFilled_RemainingAddedToBook() {
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.05"));
        orderBook.addOrder(sellOrder);

        // taker 买单数量更大 → 部分成交
        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(1, results.size());
        assertEquals(new BigDecimal("0.05"), results.get(0).getQuantity());

        // taker 应剩余 0.05 在订单簿
        assertFalse(orderBook.getAllBids().isEmpty());
        OrderVO remainingBid = orderBook.getBestBid();
        assertNotNull(remainingBid);
        assertEquals(2L, remainingBid.getOrderId());
        assertEquals(new BigDecimal("0.05"), remainingBid.getQuantity().subtract(remainingBid.getFilledQuantity()));
    }

    // ==================== sell taker 对手盘耗尽 ====================

    @Test
    void testSellTakerExhaustsAllBids() {
        OrderVO buy1 = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        OrderVO buy2 = createOrder(2L, "BUY", new BigDecimal("49900"), new BigDecimal("0.1"));
        orderBook.addOrder(buy1);
        orderBook.addOrder(buy2);

        // 卖单数量恰好等于两个买单总和
        OrderVO sellOrder = createOrder(3L, "SELL", new BigDecimal("49800"), new BigDecimal("0.2"));
        List<MatchResult> results = matchEngine.match(sellOrder, orderBook);

        assertEquals(2, results.size());

        // 所有买单应从订单簿移除
        assertTrue(orderBook.getAllBids().isEmpty());
        // taker 已完全成交，不应加入订单簿
        assertTrue(orderBook.getAllAsks().isEmpty());
    }

    // ==================== 订单簿清理已成交订单 ====================

    @Test
    void testOrderBookCleansUpFilledMakerOrders() {
        // 添加一个 seller
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        // 添加另一个 seller 在同一价格
        OrderVO sellOrder2 = createOrder(2L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder2);

        // 买单只吃掉一个 seller
        OrderVO buyOrder = createOrder(3L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        matchEngine.match(buyOrder, orderBook);

        // 应仍有一个 seller 在订单簿
        OrderVO bestAsk = orderBook.getBestAsk();
        assertNotNull(bestAsk);
        assertEquals(2L, bestAsk.getOrderId());
    }

    // ==================== 完全成交后 taker 不应加入订单簿 ====================

    @Test
    void testFullyFilledSellTakerNotAddedToBook() {
        OrderVO buyOrder = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(buyOrder);

        OrderVO sellOrder = createOrder(2L, "SELL", new BigDecimal("49000"), new BigDecimal("0.1"));
        matchEngine.match(sellOrder, orderBook);

        // taker 完全成交，不应加入订单簿
        assertTrue(orderBook.getAllAsks().isEmpty());
    }

    // ==================== 无对手盘 ====================

    @Test
    void testNoOppositeOrders_ForSellSide() {
        // 订单簿只有卖单
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        // taker 也是卖单 → 无对手盘
        OrderVO sellOrder2 = createOrder(2L, "SELL", new BigDecimal("49000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(sellOrder2, orderBook);

        assertTrue(results.isEmpty());

        // taker 应被添加到订单簿
        assertFalse(orderBook.getAllAsks().isEmpty());
    }

    // ==================== helper ====================

    private OrderVO createOrder(Long id, String side, BigDecimal price, BigDecimal quantity) {
        OrderVO order = new OrderVO();
        order.setOrderId(id);
        order.setOrderNo("ORD" + id);
        order.setAccountId(id);
        order.setSymbol("BTCUSDT");
        order.setSide(side);
        order.setOrderType("LIMIT");
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setStatus("NEW");
        return order;
    }
}
