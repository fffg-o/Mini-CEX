package com.fffg.cex.matching;

import com.fffg.cex.order.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 撮合引擎，负责对新进入的订单进行撮合。
 * <p>
 * 核心规则：
 * <ul>
 *  <li>买单：价格高优先，同价格时间早优先</li>
 *  <li> 卖单：价格低优先，同价格时间早优先</li>
 *   <li>成交价 = 先挂单价格</li>
 * </ul>
 */
@Slf4j
@Component
public class MatchEngine {

    private static final AtomicLong TRADE_SEQ = new AtomicLong(1);
    private static final String TRADE_NO_PREFIX = "TRD";

    /** maker/taker 费率 0.1% */
    private static final BigDecimal FEE_RATE = new BigDecimal("0.001");

    /**
     * 尝试将新订单与现有订单簿进行撮合。
     *
     * @param takerOrder 新进入的订单（会修改其中的 filledQuantity 字段）
     * @param orderBook  该交易对的订单簿
     * @return 撮合结果列表，可能为空
     */
    public List<MatchResult> match(OrderVO takerOrder, OrderBook orderBook) {
        List<MatchResult> results = new ArrayList<>();
        String side = takerOrder.getSide();

        log.info("开始撮合: orderId={}, side={}, symbol={}, price={}, quantity={}",
                takerOrder.getOrderId(), side, takerOrder.getSymbol(),
                takerOrder.getPrice(), takerOrder.getQuantity());

        while (true) {
            BigDecimal takerRemaining = takerOrder.getQuantity().subtract(takerOrder.getFilledQuantity());
            if (takerRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            // 获取对手方最优订单
            OrderVO makerOrder = "BUY".equals(side)
                    ? orderBook.getBestAsk()   // 买单找最便宜的卖单
                    : orderBook.getBestBid();  // 卖单找最贵的买单

            if (makerOrder == null) {
                log.debug("无对手盘订单，停止撮合");
                break;
            }

            BigDecimal makerRemaining = makerOrder.getQuantity().subtract(makerOrder.getFilledQuantity());
            if (makerRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                orderBook.removeOrder(makerOrder);
                continue;
            }

            // 检查价格是否可成交：买价 >= 卖价
            boolean canMatch;
            if ("BUY".equals(side)) {
                // taker 是买单，maker 是卖单：taker.price >= maker.price
                canMatch = takerOrder.getPrice().compareTo(makerOrder.getPrice()) >= 0;
            } else {
                // taker 是卖单，maker 是买单：maker.price >= taker.price
                canMatch = makerOrder.getPrice().compareTo(takerOrder.getPrice()) >= 0;
            }

            if (!canMatch) {
                log.debug("价格不匹配，停止撮合: takerPrice={}, makerPrice={}",
                        takerOrder.getPrice(), makerOrder.getPrice());
                break;
            }

            // 成交价 = maker 价格（先挂单方价格优先）
            BigDecimal tradePrice = makerOrder.getPrice();
            BigDecimal tradeQuantity = takerRemaining.min(makerRemaining);
            BigDecimal tradeAmount = tradePrice.multiply(tradeQuantity);

            // 计算手续费
            BigDecimal buyFee = tradeAmount.multiply(FEE_RATE).setScale(8, RoundingMode.DOWN);
            BigDecimal sellFee = tradeQuantity.multiply(FEE_RATE).setScale(8, RoundingMode.DOWN);

            // 确定买、卖单 ID
            Long buyOrderId, sellOrderId;
            Long buyAccountId, sellAccountId;
            String buyOrderNo, sellOrderNo;

            if ("BUY".equals(side)) {
                // taker 是买单，maker 是卖单
                buyOrderId = takerOrder.getOrderId();
                sellOrderId = makerOrder.getOrderId();
                buyAccountId = takerOrder.getAccountId();
                sellAccountId = makerOrder.getAccountId();
                buyOrderNo = takerOrder.getOrderNo();
                sellOrderNo = makerOrder.getOrderNo();
            } else {
                // taker 是卖单，maker 是买单
                buyOrderId = makerOrder.getOrderId();
                sellOrderId = takerOrder.getOrderId();
                buyAccountId = makerOrder.getAccountId();
                sellAccountId = takerOrder.getAccountId();
                buyOrderNo = makerOrder.getOrderNo();
                sellOrderNo = takerOrder.getOrderNo();
            }

            // 构建撮合结果
            MatchResult result = new MatchResult();
            result.setSymbol(takerOrder.getSymbol());
            result.setTradeNo(generateTradeNo());
            result.setPrice(tradePrice);
            result.setQuantity(tradeQuantity);
            result.setAmount(tradeAmount);
            result.setBuyOrderId(buyOrderId);
            result.setSellOrderId(sellOrderId);
            result.setBuyAccountId(buyAccountId);
            result.setSellAccountId(sellAccountId);
            result.setBuyOrderNo(buyOrderNo);
            result.setSellOrderNo(sellOrderNo);
            result.setBuyFee(buyFee);
            result.setSellFee(sellFee);

            // 买方结算信息
            if ("BUY".equals(side)) {
                // 买方是 taker：冻结金额 = taker.price * quantity（可能高于成交金额）
                BigDecimal buyFrozen = takerOrder.getPrice().multiply(tradeQuantity);
                result.setBuyFrozenAmount(buyFrozen);
                result.setBuyActualAmount(tradeAmount);
                result.setBuyRefundAmount(buyFrozen.subtract(tradeAmount));
                // 卖方是 maker
                result.setSellFrozenQuantity(tradeQuantity);
                result.setSellActualQuantity(tradeQuantity);
            } else {
                // 买方是 maker
                BigDecimal buyFrozen = makerOrder.getPrice().multiply(tradeQuantity);
                result.setBuyFrozenAmount(buyFrozen);
                result.setBuyActualAmount(tradeAmount);
                result.setBuyRefundAmount(buyFrozen.subtract(tradeAmount));
                // 卖方是 taker
                result.setSellFrozenQuantity(tradeQuantity);
                result.setSellActualQuantity(tradeQuantity);
            }

            results.add(result);

            // 更新已成交数量
            takerOrder.setFilledQuantity(takerOrder.getFilledQuantity().add(tradeQuantity));
            makerOrder.setFilledQuantity(makerOrder.getFilledQuantity().add(tradeQuantity));

            log.info("成交: tradeNo={}, price={}, quantity={}, amount={}, buyOrderId={}, sellOrderId={}",
                    result.getTradeNo(), tradePrice, tradeQuantity, tradeAmount,
                    buyOrderId, sellOrderId);

            // 如果 maker 完全成交，从订单簿移除
            if (makerRemaining.compareTo(tradeQuantity) <= 0) {
                orderBook.removeOrder(makerOrder);
            }

            // 如果 taker 完全成交，停止
            BigDecimal newTakerRemaining = takerOrder.getQuantity().subtract(takerOrder.getFilledQuantity());
            if (newTakerRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }

        // 如果 taker 未完全成交，加入订单簿
        BigDecimal finalRemaining = takerOrder.getQuantity().subtract(takerOrder.getFilledQuantity());
        if (finalRemaining.compareTo(BigDecimal.ZERO) > 0) {
            orderBook.addOrder(takerOrder);
            log.info("订单未完全成交，加入订单簿: orderId={}, remaining={}", takerOrder.getOrderId(), finalRemaining);
        } else {
            log.info("订单已完全成交: orderId={}", takerOrder.getOrderId());
        }

        return results;
    }

    /**
     * 生成交易编号：TRD + yyyyMMddHHmmss + 6位序列
     */
    private String generateTradeNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long seq = TRADE_SEQ.getAndIncrement();
        if (seq > 999999) {
            TRADE_SEQ.set(1);
            seq = 1;
        }
        return TRADE_NO_PREFIX + timestamp + String.format("%06d", seq);
    }
}
