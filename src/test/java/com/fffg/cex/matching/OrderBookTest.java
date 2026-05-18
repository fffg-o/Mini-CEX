package com.fffg.cex.matching;

import com.fffg.cex.order.vo.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
    }

    // ==================== addOrder ====================

    @Test
    void testAddBuyOrder() {
        OrderVO buyOrder = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(buyOrder);

        OrderVO bestBid = orderBook.getBestBid();
        assertNotNull(bestBid);
        assertEquals(1L, bestBid.getOrderId());
    }

    @Test
    void testAddSellOrder() {
        OrderVO sellOrder = createOrder(2L, "SELL", new BigDecimal("51000"), new BigDecimal("0.1"));
        orderBook.addOrder(sellOrder);

        OrderVO bestAsk = orderBook.getBestAsk();
        assertNotNull(bestAsk);
        assertEquals(2L, bestAsk.getOrderId());
    }

    @Test
    void testAddFullyFilledOrder_ShouldNotBeAdded() {
        OrderVO filledOrder = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        filledOrder.setFilledQuantity(new BigDecimal("0.1"));

        orderBook.addOrder(filledOrder);

        assertNull(orderBook.getBestBid());
    }

    @Test
    void testAddMultipleBuyOrders_PriceTimePriority() {
        OrderVO buyOrder1 = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1")); // high price
        OrderVO buyOrder2 = createOrder(2L, "BUY", new BigDecimal("49000"), new BigDecimal("0.2")); // lower price
        OrderVO buyOrder3 = createOrder(3L, "BUY", new BigDecimal("51000"), new BigDecimal("0.3")); // highest price

        orderBook.addOrder(buyOrder1);
        orderBook.addOrder(buyOrder2);
        orderBook.addOrder(buyOrder3);

        // Best bid should be the highest price (51000)
        OrderVO best = orderBook.getBestBid();
        assertEquals(3L, best.getOrderId());
        assertEquals(new BigDecimal("51000"), best.getPrice());
    }

    @Test
    void testAddMultipleSellOrders_PriceTimePriority() {
        OrderVO sellOrder1 = createOrder(1L, "SELL", new BigDecimal("51000"), new BigDecimal("0.1"));
        OrderVO sellOrder2 = createOrder(2L, "SELL", new BigDecimal("50000"), new BigDecimal("0.2")); // lower price
        OrderVO sellOrder3 = createOrder(3L, "SELL", new BigDecimal("52000"), new BigDecimal("0.3"));

        orderBook.addOrder(sellOrder1);
        orderBook.addOrder(sellOrder2);
        orderBook.addOrder(sellOrder3);

        // Best ask should be the lowest price (50000)
        OrderVO best = orderBook.getBestAsk();
        assertEquals(2L, best.getOrderId());
        assertEquals(new BigDecimal("50000"), best.getPrice());
    }

    // ==================== removeOrder ====================

    @Test
    void testRemoveOrder() {
        OrderVO buyOrder = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(buyOrder);
        orderBook.removeOrder(buyOrder);

        assertNull(orderBook.getBestBid());
    }

    @Test
    void testRemoveNonExistentOrder() {
        OrderVO order = createOrder(999L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        // Should not throw
        orderBook.removeOrder(order);
    }

    @Test
    void testRemoveOrder_ClearsPriceLevel() {
        OrderVO order1 = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        orderBook.addOrder(order1);
        orderBook.removeOrder(order1);

        // Price level should be removed
        List<String[]> bidsSnapshot = orderBook.getBidsSnapshot(10);
        assertTrue(bidsSnapshot.isEmpty());
    }

    @Test
    void testRemoveOrder_MultipleAtSamePrice() {
        OrderVO order1 = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        OrderVO order2 = createOrder(2L, "BUY", new BigDecimal("50000"), new BigDecimal("0.2"));
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);

        orderBook.removeOrder(order1);
        // order2 should still be there
        OrderVO best = orderBook.getBestBid();
        assertEquals(2L, best.getOrderId());
    }

    // ==================== getBestBid / getBestAsk ====================

    @Test
    void testGetBestBid_EmptyBook() {
        assertNull(orderBook.getBestBid());
    }

    @Test
    void testGetBestAsk_EmptyBook() {
        assertNull(orderBook.getBestAsk());
    }

    @Test
    void testGetBestBid_SkipsFilledOrders() {
        OrderVO buyOrder1 = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        OrderVO buyOrder2 = createOrder(2L, "BUY", new BigDecimal("49000"), new BigDecimal("0.2"));
        buyOrder1.setFilledQuantity(new BigDecimal("0.1")); // fully filled

        orderBook.addOrder(buyOrder1);
        orderBook.addOrder(buyOrder2);

        // Should skip the filled one and return the next best
        OrderVO best = orderBook.getBestBid();
        assertEquals(2L, best.getOrderId());
        assertEquals(new BigDecimal("49000"), best.getPrice());
    }

    // ==================== snapshot ====================

    @Test
    void testGetBidsSnapshot() {
        OrderVO buy1 = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        OrderVO buy2 = createOrder(2L, "BUY", new BigDecimal("49000"), new BigDecimal("0.2"));
        OrderVO buy3 = createOrder(3L, "BUY", new BigDecimal("50000"), new BigDecimal("0.3"));

        orderBook.addOrder(buy1);
        orderBook.addOrder(buy2);
        orderBook.addOrder(buy3);

        List<String[]> snapshot = orderBook.getBidsSnapshot(10);
        assertEquals(2, snapshot.size()); // Two price levels: 50000 and 49000
        assertEquals("50000", snapshot.get(0)[0]);
        assertEquals("0.4", snapshot.get(0)[1]); // 0.1 + 0.3
        assertEquals("49000", snapshot.get(1)[0]);
        assertEquals("0.2", snapshot.get(1)[1]);
    }

    @Test
    void testGetAsksSnapshot() {
        OrderVO sell1 = createOrder(1L, "SELL", new BigDecimal("51000"), new BigDecimal("0.1"));
        OrderVO sell2 = createOrder(2L, "SELL", new BigDecimal("52000"), new BigDecimal("0.2"));

        orderBook.addOrder(sell1);
        orderBook.addOrder(sell2);

        List<String[]> snapshot = orderBook.getAsksSnapshot(10);
        assertEquals(2, snapshot.size());
        assertEquals("51000", snapshot.get(0)[0]); // lowest price first
        assertEquals("52000", snapshot.get(1)[0]);
    }

    @Test
    void testGetSnapshotWithLimit() {
        for (int i = 0; i < 10; i++) {
            OrderVO buy = createOrder((long) i, "BUY", new BigDecimal("50000").subtract(new BigDecimal(i * 100)), new BigDecimal("0.1"));
            orderBook.addOrder(buy);
        }

        List<String[]> snapshot = orderBook.getBidsSnapshot(3);
        assertEquals(3, snapshot.size());
    }

    // ==================== getAllBids / getAllAsks ====================

    @Test
    void testGetAllBids() {
        OrderVO buy1 = createOrder(1L, "BUY", new BigDecimal("50000"), new BigDecimal("0.1"));
        OrderVO buy2 = createOrder(2L, "BUY", new BigDecimal("49000"), new BigDecimal("0.2"));
        orderBook.addOrder(buy1);
        orderBook.addOrder(buy2);

        List<OrderVO> allBids = orderBook.getAllBids();
        assertEquals(2, allBids.size());
    }

    @Test
    void testGetAllAsks() {
        OrderVO sell1 = createOrder(1L, "SELL", new BigDecimal("51000"), new BigDecimal("0.1"));
        orderBook.addOrder(sell1);

        List<OrderVO> allAsks = orderBook.getAllAsks();
        assertEquals(1, allAsks.size());
    }

    // ==================== helper ====================

    private OrderVO createOrder(Long id, String side, BigDecimal price, BigDecimal quantity) {
        OrderVO order = new OrderVO();
        order.setOrderId(id);
        order.setSide(side);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setStatus("NEW");
        return order;
    }
}
