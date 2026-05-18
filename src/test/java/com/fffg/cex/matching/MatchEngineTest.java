package com.fffg.cex.matching;

import com.fffg.cex.order.vo.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchEngineTest {

    private MatchEngine matchEngine;
    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        matchEngine = new MatchEngine();
        orderBook = new OrderBook();
    }

    @Test
    void testNoMatch_NoOppositeOrders() {
        OrderVO buyOrder = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertTrue(results.isEmpty());
    }

    @Test
    void testBuyOrderMatchesWithSellOrder() {
        // Pre-place a sell order in the book
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        // Taker buy order at a higher price
        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(1, results.size());
        MatchResult result = results.get(0);
        assertEquals(0, new BigDecimal("50000").compareTo(result.getPrice())); //成交价 = maker价格
        assertEquals(0, new BigDecimal("0.1").compareTo(result.getQuantity()));
        assertEquals(0, new BigDecimal("5000").compareTo(result.getAmount())); // 50000 * 0.1
        assertEquals(2L, result.getBuyOrderId());
        assertEquals(1L, result.getSellOrderId());
    }

    @Test
    void testSellOrderMatchesWithBuyOrder() {
        // Pre-place a buy order in the book
        OrderVO buyOrder = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(buyOrder);

        // Taker sell order at a lower price
        OrderVO sellOrder = createOrder(2L, "SELL", new BigDecimal("49000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(sellOrder, orderBook);

        assertEquals(1, results.size());
        MatchResult result = results.get(0);
        assertEquals(1L, result.getBuyOrderId());
        assertEquals(2L, result.getSellOrderId());
    }

    @Test
    void testNoMatch_PriceNotMet_Buy() {
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("51000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        // Taker buy order at price lower than the sell order
        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertTrue(results.isEmpty());
    }

    @Test
    void testNoMatch_PriceNotMet_Sell() {
        OrderVO buyOrder = createOrder(1L, "BUY", new BigDecimal("49000"), new BigDecimal("0.1"));
        orderBook.addOrder(buyOrder);

        // Taker sell order at price higher than the buy order
        OrderVO sellOrder = createOrder(2L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(sellOrder, orderBook);

        assertTrue(results.isEmpty());
    }

    @Test
    void testPartialFill() {
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.05"));
        orderBook.addOrder(sellOrder);

        // Taker wants more than available
        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(1, results.size());
        assertEquals(new BigDecimal("0.05"), results.get(0).getQuantity());

        // Buyer should have 0.05 filled
        assertEquals(new BigDecimal("0.05"), buyOrder.getFilledQuantity());
        // Seller should be fully filled
        assertEquals(new BigDecimal("0.05"), sellOrder.getFilledQuantity());
    }

    @Test
    void testMultipleMatches() {
        // Place two sell orders
        OrderVO sellOrder1 = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.05"));
        OrderVO sellOrder2 = createOrder(2L, "SELL", new BigDecimal("50100"), new BigDecimal("0.05"));
        orderBook.addOrder(sellOrder1);
        orderBook.addOrder(sellOrder2);

        // Large buy order that can match with both
        OrderVO buyOrder = createOrder(3L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(2, results.size());

        // First match with the cheaper sell order
        assertEquals(new BigDecimal("50000"), results.get(0).getPrice());
        assertEquals(new BigDecimal("0.05"), results.get(0).getQuantity());

        // Second match
        assertEquals(new BigDecimal("50100"), results.get(1).getPrice());
        assertEquals(new BigDecimal("0.05"), results.get(1).getQuantity());

        // Buyer fully filled
        assertEquals(0, new BigDecimal("0.1").compareTo(buyOrder.getFilledQuantity()));
    }

    @Test
    void testFilledOrderRemovedFromBook() {
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        matchEngine.match(buyOrder, orderBook);

        // Sell order should be removed from order book
        assertNull(orderBook.getBestAsk());
    }

    @Test
    void testPartiallyFilledOrderAddedToBook() {
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.05"));
        orderBook.addOrder(sellOrder);

        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        matchEngine.match(buyOrder, orderBook);

        // Buy order was partially filled (0.05 out of 0.1), should be added to book
        OrderVO bestBid = orderBook.getBestBid();
        assertNotNull(bestBid);
        assertEquals(2L, bestBid.getOrderId());
        assertEquals(new BigDecimal("0.05"), bestBid.getQuantity().subtract(bestBid.getFilledQuantity()));
    }

    @Test
    void testFullyFilledTakerNotAddedToBook() {
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        matchEngine.match(buyOrder, orderBook);

        // Buy order was fully filled, should NOT be added to book
        assertTrue(orderBook.getAllBids().isEmpty());
    }

    @Test
    void testSamePriceOrders() {
        OrderVO sellOrder1 = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.05"));
        OrderVO sellOrder2 = createOrder(2L, "SELL", new BigDecimal("50000"), new BigDecimal("0.05"));
        orderBook.addOrder(sellOrder1);
        orderBook.addOrder(sellOrder2);

        OrderVO buyOrder = createOrder(3L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(2, results.size());
    }

    @Test
    void testFeeCalculation() {
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("1"));
        orderBook.addOrder(sellOrder);

        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(1, results.size());
        MatchResult result = results.get(0);

        // Buy fee = amount * 0.001 = 50000 * 0.001 = 50
        assertEquals(new BigDecimal("50.00000000"), result.getBuyFee());
        // Sell fee = quantity * 0.001 = 1 * 0.001 = 0.001
        assertEquals(new BigDecimal("0.00100000"), result.getSellFee());
    }

    @Test
    void testBuyRefundAmount() {
        OrderVO sellOrder = createOrder(1L, "SELL", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        // Buy order at higher price - should get refund
        OrderVO buyOrder = createOrder(2L, "BUY", new BigDecimal("51000"), new BigDecimal("0.1"));
        List<MatchResult> results = matchEngine.match(buyOrder, orderBook);

        assertEquals(1, results.size());
        MatchResult result = results.get(0);

        // buyFrozenAmount = 51000 * 0.1 = 5100 (scale 1, value 5100.0)
        assertEquals(0, new BigDecimal("5100").compareTo(result.getBuyFrozenAmount()));
        // buyActualAmount = 50000 * 0.1 = 5000 (scale 1, value 5000.0)
        assertEquals(0, new BigDecimal("5000").compareTo(result.getBuyActualAmount()));
        // buyRefundAmount = 5100 - 5000 = 100
        assertEquals(0, new BigDecimal("100").compareTo(result.getBuyRefundAmount()));
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
