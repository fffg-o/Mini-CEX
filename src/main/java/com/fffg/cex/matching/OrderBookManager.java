package com.fffg.cex.matching;

import com.fffg.cex.order.mapper.OrderMapper;
import com.fffg.cex.order.vo.OrderVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 订单簿管理器，按交易对管理内存订单簿和锁。
 * <p>
 * 核心职责：
 * <ol>
 *   <li>管理每个交易对的 {@link ReentrantLock}，确保下单、撤单、撮合互斥</li>
 *   <li>管理每个交易对的内存 {@link OrderBook}</li>
 *   <li>服务启动时从数据库加载活跃订单到内存</li>
 * </ol>
 */
@Slf4j
@Component
public class OrderBookManager {

    /** 每个交易对的锁 */
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    /** 每个交易对的订单簿 */
    private final ConcurrentHashMap<String, OrderBook> orderBookMap = new ConcurrentHashMap<>();

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 启动时加载数据库中所有活跃订单（NEW / PARTIALLY_FILLED）到内存订单簿。
     */
    @PostConstruct
    public void init() {
        log.info("开始加载活跃订单到内存订单簿...");
        List<OrderVO> activeOrders = orderMapper.selectActiveOrders();
        int count = 0;
        for (OrderVO order : activeOrders) {
            OrderBook book = orderBookMap.computeIfAbsent(order.getSymbol(), k -> new OrderBook());
            book.addOrder(order);
            count++;
        }
        log.info("内存订单簿加载完成，共加载 {} 个活跃订单", count);

        // 打印每个交易对的订单簿概况
        orderBookMap.forEach((symbol, book) -> {
            int bidCount = book.getAllBids().size();
            int askCount = book.getAllAsks().size();
            log.info("交易对 {}: 买盘 {} 单, 卖盘 {} 单", symbol, bidCount, askCount);
        });
    }

    /**
     * 在交易对锁内执行操作（有返回值）。
     *
     * @param symbol 交易对
     * @param action 需要执行的逻辑
     * @param <T>    返回值类型
     * @return 执行结果
     */
    public <T> T executeWithLock(String symbol, Supplier<T> action) {
        ReentrantLock lock = lockMap.computeIfAbsent(symbol.toUpperCase(), k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 在交易对锁内执行操作（无返回值）。
     *
     * @param symbol 交易对
     * @param action 需要执行的逻辑
     */
    public void executeWithLock(String symbol, Runnable action) {
        ReentrantLock lock = lockMap.computeIfAbsent(symbol.toUpperCase(), k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取指定交易对的订单簿（如果不存在则创建）。
     */
    public OrderBook getOrderBook(String symbol) {
        return orderBookMap.computeIfAbsent(symbol.toUpperCase(), k -> new OrderBook());
    }

    /**
     * 判断指定交易对是否有锁竞争（用于监控）。
     */
    public boolean hasQueuedThreads(String symbol) {
        ReentrantLock lock = lockMap.get(symbol.toUpperCase());
        return lock != null && lock.hasQueuedThreads();
    }
}
