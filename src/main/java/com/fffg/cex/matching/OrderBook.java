package com.fffg.cex.matching;

import com.fffg.cex.order.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 内存订单簿，按交易对维护买盘和卖盘。
 * <p>
 * 买盘（bids）：价格从高到低，同价格按时间从早到晚。
 * 卖盘（asks）：价格从低到高，同价格按时间从早到晚。
 * <p>
 * 不是线程安全的，调用方需通过 OrderBookManager 获取锁后使用。
 */
@Slf4j
public class OrderBook {

    /** 买盘：价格从高到低 -> 该价格下所有订单（按时间从早到晚） */
    private final TreeMap<BigDecimal, LinkedList<OrderVO>> bids;

    /** 卖盘：价格从低到高 -> 该价格下所有订单（按时间从早到晚） */
    private final TreeMap<BigDecimal, LinkedList<OrderVO>> asks;

    public OrderBook() {
        // 买盘：价格降序（高价优先）
        this.bids = new TreeMap<>(Comparator.reverseOrder());
        // 卖盘：价格升序（低价优先）
        this.asks = new TreeMap<>(Comparator.naturalOrder());
    }

    // ==================== 添加订单 ====================

    /**
     * 向订单簿添加一个订单。
     * 如果订单已完全成交，不加入。
     */
    public void addOrder(OrderVO order) {
        BigDecimal remaining = order.getQuantity().subtract(order.getFilledQuantity());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        TreeMap<BigDecimal, LinkedList<OrderVO>> book = getBook(order.getSide());
        book.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
        log.debug("订单簿添加订单: orderId={}, side={}, price={}, remaining={}",
                order.getOrderId(), order.getSide(), order.getPrice(), remaining);
    }

    // ==================== 移除订单 ====================

    /**
     * 从订单簿移除一个订单（完全成交或撤销时调用）。
     */
    public void removeOrder(OrderVO order) {
        TreeMap<BigDecimal, LinkedList<OrderVO>> book = getBook(order.getSide());
        LinkedList<OrderVO> ordersAtPrice = book.get(order.getPrice());
        if (ordersAtPrice == null) {
            return;
        }
        ordersAtPrice.removeIf(o -> o.getOrderId().equals(order.getOrderId()));
        if (ordersAtPrice.isEmpty()) {
            book.remove(order.getPrice());
        }
        log.debug("订单簿移除订单: orderId={}, side={}, price={}", order.getOrderId(), order.getSide(), order.getPrice());
    }

    // ==================== 查询最优对手盘 ====================

    /**
     * 获取最优买单（价格最高者）。
     * 如果多个订单同价，返回最早的那个。
     */
    public OrderVO getBestBid() {
        return getBestOrder(bids);
    }

    /**
     * 获取最优卖单（价格最低者）。
     * 如果多个订单同价，返回最早的那个。
     */
    public OrderVO getBestAsk() {
        return getBestOrder(asks);
    }

    private OrderVO getBestOrder(TreeMap<BigDecimal, LinkedList<OrderVO>> book) {
        if (book.isEmpty()) {
            return null;
        }
        Map.Entry<BigDecimal, LinkedList<OrderVO>> firstEntry = book.firstEntry();
        LinkedList<OrderVO> orders = firstEntry.getValue();
        if (orders == null || orders.isEmpty()) {
            book.remove(firstEntry.getKey());
            return getBestOrder(book);
        }
        OrderVO best = orders.getFirst();
        // 检查该订单是否还有剩余数量
        BigDecimal remaining = best.getQuantity().subtract(best.getFilledQuantity());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            // 已完全成交，移除并递归找下一个
            orders.removeFirst();
            if (orders.isEmpty()) {
                book.remove(firstEntry.getKey());
            }
            return getBestOrder(book);
        }
        return best;
    }

    // ==================== 快照 / 查询 ====================

    /**
     * 获取买盘快照（价格从高到低），每个价格 level 汇总数量。
     *
     * @param limit 最多返回的档位数
     * @return 列表 [price, quantity][]
     */
    public List<String[]> getBidsSnapshot(int limit) {
        return getSnapshot(bids, limit);
    }

    /**
     * 获取卖盘快照（价格从低到高），每个价格 level 汇总数量。
     *
     * @param limit 最多返回的档位数
     * @return 列表 [price, quantity][]
     */
    public List<String[]> getAsksSnapshot(int limit) {
        return getSnapshot(asks, limit);
    }

    private List<String[]> getSnapshot(TreeMap<BigDecimal, LinkedList<OrderVO>> book, int limit) {
        return book.entrySet().stream()
                .limit(limit)
                .map(entry -> {
                    BigDecimal totalQty = entry.getValue().stream()
                            .map(o -> o.getQuantity().subtract(o.getFilledQuantity()))
                            .filter(q -> q.compareTo(BigDecimal.ZERO) > 0)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new String[]{
                            entry.getKey().toPlainString(),
                            totalQty.toPlainString()
                    };
                })
                .filter(arr -> new BigDecimal(arr[1]).compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
    }

    // ==================== 内部工具 ====================

    private TreeMap<BigDecimal, LinkedList<OrderVO>> getBook(String side) {
        return "BUY".equals(side) ? bids : asks;
    }

    /**
     * 获取所有活跃的买单（用于重启恢复）
     */
    public List<OrderVO> getAllBids() {
        return bids.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有活跃的卖单（用于重启恢复）
     */
    public List<OrderVO> getAllAsks() {
        return asks.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }
}
