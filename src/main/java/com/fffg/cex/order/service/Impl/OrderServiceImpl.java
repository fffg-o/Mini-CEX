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
import com.fffg.cex.trade.vo.TradeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountBalanceMapper accountBalanceMapper;

    @Autowired
    private AssetLedgerMapper assetLedgerMapper;

    @Autowired
    private SymbolPairMapper symbolPairMapper;

    @Autowired
    private OrderBookManager orderBookManager;

    @Autowired
    private MatchEngine matchEngine;

    @Autowired
    private TradeMapper tradeMapper;

    /**
     * 订单号序列计数器
     */
    private static final AtomicLong ORDER_SEQ = new AtomicLong(1);

    /**
     * 订单号前缀
     */
    private static final String ORDER_NO_PREFIX = "ORD";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(CreateOrderRequestDTO request) {
        String symbol = request.getSymbol().toUpperCase();
        String side = request.getSide().toUpperCase();
        String orderType = request.getOrderType().toUpperCase();

        // 在交易对锁内执行：校验、冻结、下单、撮合
        return orderBookManager.executeWithLock(symbol, () -> {
            // 1. 校验账户存在
            AccountVO account = accountMapper.getAccountById(request.getAccountId());
            if (account == null) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(),
                        ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
            }

            // 2. 校验交易对存在且启用
            SymbolPairVO symbolPair = symbolPairMapper.selectBySymbol(symbol);
            if (symbolPair == null) {
                throw new BusinessException(ErrorCode.SYMBOL_NOT_FOUND.getCode(),
                        ErrorCode.SYMBOL_NOT_FOUND.getMessage());
            }
            if (symbolPair.getStatus() == null || symbolPair.getStatus() != 1) {
                throw new BusinessException(ErrorCode.SYMBOL_NOT_FOUND.getCode(), "交易对未启用");
            }

            // 3. 校验订单类型（第一阶段只支持 LIMIT）
            if (!"LIMIT".equals(orderType)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "第一阶段只支持 LIMIT 限价单");
            }

            BigDecimal price = request.getPrice();
            BigDecimal quantity = request.getQuantity();

            // 4. 精度校验
            if (price.scale() > symbolPair.getPriceScale()) {
                throw new BusinessException(ErrorCode.PRICE_SCALE_INVALID.getCode(),
                        "价格小数位数不能超过" + symbolPair.getPriceScale() + "位");
            }
            if (quantity.scale() > symbolPair.getQuantityScale()) {
                throw new BusinessException(ErrorCode.QUANTITY_SCALE_INVALID.getCode(),
                        "数量小数位数不能超过" + symbolPair.getQuantityScale() + "位");
            }

            // 5. 确定冻结资产类型和数量
            String freezeAsset;
            BigDecimal freezeAmount;

            if ("BUY".equals(side)) {
                // 买单冻结计价币（quoteAsset），例如 BTCUSDT 冻结 USDT
                freezeAsset = symbolPair.getQuoteAsset();
                // 冻结金额 = price * quantity，按 priceScale 截断（舍去多余小数位）
                freezeAmount = price.multiply(quantity).setScale(symbolPair.getPriceScale(), RoundingMode.DOWN);

                // 最小订单金额校验
                if (freezeAmount.compareTo(symbolPair.getMinOrderAmount()) < 0) {
                    throw new BusinessException(ErrorCode.ORDER_AMOUNT_TOO_SMALL.getCode(),
                            "订单金额 " + freezeAmount + " 小于最小交易金额 " + symbolPair.getMinOrderAmount());
                }
            } else {
                // 卖单冻结基础币（baseAsset），例如 BTCUSDT 冻结 BTC
                freezeAsset = symbolPair.getBaseAsset();
                freezeAmount = quantity;
            }

            // 6. 查询当前余额（用于流水记录）
            AccountBalanceVO balance = accountBalanceMapper.selectByAccountIdAndAsset(
                    request.getAccountId(), freezeAsset);
            BigDecimal beforeAvailable;
            BigDecimal beforeFrozen;

            if (balance == null) {
                beforeAvailable = BigDecimal.ZERO;
                beforeFrozen = BigDecimal.ZERO;
            } else {
                beforeAvailable = balance.getAvailableBalance();
                beforeFrozen = balance.getFrozenBalance();
            }

            // 7. 条件更新冻结资产（防止超扣）
            int updatedRows = accountBalanceMapper.freezeBalance(
                    request.getAccountId(), freezeAsset, freezeAmount);
            if (updatedRows != 1) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE.getCode(),
                        freezeAsset + " 可用余额不足，需要 " + freezeAmount);
            }

            BigDecimal afterAvailable = beforeAvailable.subtract(freezeAmount);
            BigDecimal afterFrozen = beforeFrozen.add(freezeAmount);

            // 8. 生成订单号并创建订单
            String orderNo = generateOrderNo();
            OrderVO orderVO = new OrderVO();
            orderVO.setOrderNo(orderNo);
            orderVO.setAccountId(request.getAccountId());
            orderVO.setSymbol(symbol);
            orderVO.setSide(side);
            orderVO.setOrderType(orderType);
            orderVO.setPrice(price);
            orderVO.setQuantity(quantity);

            orderMapper.insert(orderVO);
            Long orderId = orderVO.getOrderId();

            // 9. 插入资产流水（冻结）
            AssetLedgerRecord ledger = new AssetLedgerRecord();
            ledger.setAccountId(request.getAccountId());
            ledger.setAssetSymbol(freezeAsset);
            ledger.setBusinessType("ORDER_FREEZE");
            ledger.setBusinessId(orderNo);
            ledger.setChangeAvailable(freezeAmount.negate());
            ledger.setChangeFrozen(freezeAmount);
            ledger.setBeforeAvailable(beforeAvailable);
            ledger.setAfterAvailable(afterAvailable);
            ledger.setBeforeFrozen(beforeFrozen);
            ledger.setAfterFrozen(afterFrozen);
            assetLedgerMapper.insert(ledger);

            // 10. 重新查询完整订单（包含 status）
            OrderVO createdOrder = orderMapper.selectById(orderId);

            // 11. 调用撮合引擎尝试撮合
            OrderBook orderBook = orderBookManager.getOrderBook(symbol);
            List<MatchResult> matchResults = matchEngine.match(createdOrder, orderBook);

            // 12. 处理撮合结果：成交结算
            if (!matchResults.isEmpty()) {
                processMatchResults(matchResults, symbolPair);
                // 重新查询订单（状态可能已变为 FILLED / PARTIALLY_FILLED）
                createdOrder = orderMapper.selectById(orderId);
            }

            log.info("订单创建完成: orderId={}, orderNo={}, status={}, filled={}/{}",
                    orderId, orderNo, createdOrder.getStatus(),
                    createdOrder.getFilledQuantity(), createdOrder.getQuantity());

            return createdOrder;
        });
    }

    @Override
    public OrderVO getOrderById(Long orderId) {
        OrderVO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND.getCode(),
                    ErrorCode.ORDER_NOT_FOUND.getMessage());
        }
        return order;
    }

    @Override
    public PageVO<OrderVO> getOrdersByAccount(Long accountId, String symbol, String side,
                                              String status, int pageNum, int pageSize) {
        // 先校验账户是否存在
        AccountVO account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(),
                    ErrorCode.ACCOUNT_NOT_FOUND.getMessage());
        }

        int offset = (pageNum - 1) * pageSize;
        List<OrderVO> records = orderMapper.selectPage(accountId, symbol, side, status, offset, pageSize);
        long total = orderMapper.countByCondition(accountId, symbol, side, status);
        return new PageVO<>(records, pageNum, pageSize, total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCancelVO cancelOrder(Long orderId) {
        // 1. 查询订单是否存在
        OrderVO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND.getCode(),
                    ErrorCode.ORDER_NOT_FOUND.getMessage());
        }

        // 在交易对锁内执行撤单，避免与撮合并发冲突
        return orderBookManager.executeWithLock(order.getSymbol(), () -> {
            // 重新查询订单（在锁内确保状态最新）
            OrderVO lockedOrder = orderMapper.selectById(orderId);
            if (lockedOrder == null) {
                throw new BusinessException(ErrorCode.ORDER_NOT_FOUND.getCode(),
                        ErrorCode.ORDER_NOT_FOUND.getMessage());
            }

            // 2. 校验订单状态
            String status = lockedOrder.getStatus();
            if ("FILLED".equals(status)) {
                throw new BusinessException(ErrorCode.ORDER_FULLY_FILLED.getCode(),
                        ErrorCode.ORDER_FULLY_FILLED.getMessage());
            }
            if ("CANCELED".equals(status)) {
                throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELED.getCode(),
                        ErrorCode.ORDER_ALREADY_CANCELED.getMessage());
            }

            // 3. 计算剩余未成交数量
            BigDecimal remainingQuantity = lockedOrder.getQuantity().subtract(lockedOrder.getFilledQuantity());

            // 4. 查询交易对信息，确定解冻资产类型和数量
            SymbolPairVO symbolPair = symbolPairMapper.selectBySymbol(lockedOrder.getSymbol());
            if (symbolPair == null) {
                throw new BusinessException(ErrorCode.SYMBOL_NOT_FOUND.getCode(),
                        ErrorCode.SYMBOL_NOT_FOUND.getMessage());
            }

            String unfreezeAsset;
            BigDecimal unfreezeAmount;

            if ("BUY".equals(lockedOrder.getSide())) {
                // 买单解冻 USDT：price * remainingQuantity
                unfreezeAsset = symbolPair.getQuoteAsset();
                unfreezeAmount = lockedOrder.getPrice().multiply(remainingQuantity)
                        .setScale(symbolPair.getPriceScale(), RoundingMode.DOWN);
            } else {
                // 卖单解冻 BTC：remainingQuantity
                unfreezeAsset = symbolPair.getBaseAsset();
                unfreezeAmount = remainingQuantity;
            }

            // 5. 查询当前余额（用于流水记录）
            AccountBalanceVO balance = accountBalanceMapper.selectByAccountIdAndAsset(
                    lockedOrder.getAccountId(), unfreezeAsset);
            BigDecimal beforeAvailable = (balance != null) ? balance.getAvailableBalance() : BigDecimal.ZERO;
            BigDecimal beforeFrozen = (balance != null) ? balance.getFrozenBalance() : BigDecimal.ZERO;

            // 6. 条件更新解冻资产
            int updatedRows = accountBalanceMapper.unfreezeBalance(
                    lockedOrder.getAccountId(), unfreezeAsset, unfreezeAmount);
            if (updatedRows != 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(),
                        "解冻资产失败，冻结余额不足");
            }

            BigDecimal afterAvailable = beforeAvailable.add(unfreezeAmount);
            BigDecimal afterFrozen = beforeFrozen.subtract(unfreezeAmount);

            // 7. 更新订单状态为 CANCELED
            orderMapper.updateStatus(orderId, "CANCELED");

            // 8. 从内存订单簿移除
            OrderBook orderBook = orderBookManager.getOrderBook(lockedOrder.getSymbol());
            orderBook.removeOrder(lockedOrder);

            // 9. 插入资产流水
            AssetLedgerRecord ledger = new AssetLedgerRecord();
            ledger.setAccountId(lockedOrder.getAccountId());
            ledger.setAssetSymbol(unfreezeAsset);
            ledger.setBusinessType("ORDER_UNFREEZE");
            ledger.setBusinessId(lockedOrder.getOrderNo());
            ledger.setChangeAvailable(unfreezeAmount);
            ledger.setChangeFrozen(unfreezeAmount.negate());
            ledger.setBeforeAvailable(beforeAvailable);
            ledger.setAfterAvailable(afterAvailable);
            ledger.setBeforeFrozen(beforeFrozen);
            ledger.setAfterFrozen(afterFrozen);
            assetLedgerMapper.insert(ledger);

            log.info("订单撤销成功: orderId={}, remainingQuantity={}, unfreezeAsset={}, unfreezeAmount={}",
                    orderId, remainingQuantity, unfreezeAsset, unfreezeAmount);

            return new OrderCancelVO(orderId, "CANCELED");
        });
    }

    // ==================== 私有方法 ====================

    /**
     * 处理撮合结果：生成成交记录、资产结算、更新订单状态、插入流水。
     */
    private void processMatchResults(List<MatchResult> matchResults, SymbolPairVO symbolPair) {
        for (MatchResult result : matchResults) {
            // 1. 插入成交记录
            TradeVO tradeVO = new TradeVO();
            tradeVO.setTradeNo(result.getTradeNo());
            tradeVO.setSymbol(result.getSymbol());
            tradeVO.setPrice(result.getPrice());
            tradeVO.setQuantity(result.getQuantity());
            tradeVO.setAmount(result.getAmount());
            tradeVO.setBuyOrderId(result.getBuyOrderId());
            tradeVO.setSellOrderId(result.getSellOrderId());
            tradeVO.setBuyFee(result.getBuyFee());
            tradeVO.setSellFee(result.getSellFee());
            tradeMapper.insert(tradeVO);

            // 2. 买方结算
            settleBuyer(result, symbolPair);

            // 3. 卖方结算
            settleSeller(result, symbolPair);

            // 4. 更新买单状态（传入本次成交数量作为增量）
            updateOrderAfterTrade(result.getBuyOrderId(), result.getBuyOrderNo(), result.getQuantity());

            // 5. 更新卖单状态（传入本次成交数量作为增量）
            updateOrderAfterTrade(result.getSellOrderId(), result.getSellOrderNo(), result.getQuantity());
        }
    }

    /**
     * 买方结算：
     * <ul>
     *   <li>扣除 USDT 冻结（按实际成交金额）</li>
     *   <li>增加 BTC 可用余额</li>
     *   <li>如果有价差，退还 USDT 差额</li>
     *   <li>扣除买方手续费</li>
     * </ul>
     */
    private void settleBuyer(MatchResult result, SymbolPairVO symbolPair) {
        Long buyerAccountId = result.getBuyAccountId();
        String quoteAsset = symbolPair.getQuoteAsset(); // USDT
        String baseAsset = symbolPair.getBaseAsset();   // BTC

        BigDecimal tradeAmount = result.getAmount();
        BigDecimal tradeQuantity = result.getQuantity();
        BigDecimal buyFee = result.getBuyFee();
        BigDecimal refundAmount = result.getBuyRefundAmount();

        // 2a. 扣除 USDT 冻结（按实际成交金额）
        AccountBalanceVO usdtBalance = accountBalanceMapper.selectByAccountIdAndAsset(buyerAccountId, quoteAsset);
        if (usdtBalance == null) {
            log.error("买方 USDT 余额记录不存在: accountId={}", buyerAccountId);
            return;
        }

        // 减少 USDT frozen
        BigDecimal beforeUsdtFrozen = usdtBalance.getFrozenBalance();
        BigDecimal afterUsdtFrozen = beforeUsdtFrozen.subtract(tradeAmount);
        int rows = accountBalanceMapper.subtractFrozenBalance(buyerAccountId, quoteAsset, tradeAmount);
        if (rows != 1) {
            log.error("买方 USDT 冻结扣减失败: accountId={}, amount={}", buyerAccountId, tradeAmount);
            return;
        }

        // 流水：买方扣除 USDT 冻结
        insertLedger(buyerAccountId, quoteAsset, "TRADE_BUY_PAY",
                result.getTradeNo() + "_BUY_PAY",
                BigDecimal.ZERO, tradeAmount.negate(),
                usdtBalance.getAvailableBalance(), usdtBalance.getAvailableBalance(),
                beforeUsdtFrozen, afterUsdtFrozen);

        // 如果有价差退款
        BigDecimal beforeUsdtAvailable = usdtBalance.getAvailableBalance();
        BigDecimal afterUsdtAvailable = beforeUsdtAvailable;

        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            accountBalanceMapper.addAvailableBalance(buyerAccountId, quoteAsset, refundAmount);
            afterUsdtAvailable = beforeUsdtAvailable.add(refundAmount);

            // 流水：价差退款
            insertLedger(buyerAccountId, quoteAsset, "TRADE_REFUND",
                    result.getTradeNo() + "_REFUND",
                    refundAmount, BigDecimal.ZERO,
                    beforeUsdtAvailable, afterUsdtAvailable,
                    afterUsdtFrozen, afterUsdtFrozen);
        }

        // 扣除买方手续费（USDT）
        if (buyFee.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal beforeFeeAvailable = afterUsdtAvailable;
            BigDecimal afterFeeAvailable = beforeFeeAvailable.subtract(buyFee);
            rows = accountBalanceMapper.subtractAvailableBalance(buyerAccountId, quoteAsset, buyFee);
            if (rows != 1) {
                log.error("买方手续费扣减失败: accountId={}, fee={}", buyerAccountId, buyFee);
            } else {
                // 流水：手续费
                insertLedger(buyerAccountId, quoteAsset, "FEE",
                        result.getTradeNo() + "_BUY_FEE",
                        buyFee.negate(), BigDecimal.ZERO,
                        beforeFeeAvailable, afterFeeAvailable,
                        afterUsdtFrozen, afterUsdtFrozen);
                afterUsdtAvailable = afterFeeAvailable;
            }
        }

        // 2b. 增加 BTC 可用余额
        AccountBalanceVO btcBalance = accountBalanceMapper.selectByAccountIdAndAsset(buyerAccountId, baseAsset);
        BigDecimal beforeBtcAvailable = (btcBalance != null) ? btcBalance.getAvailableBalance() : BigDecimal.ZERO;
        BigDecimal beforeBtcFrozen = (btcBalance != null) ? btcBalance.getFrozenBalance() : BigDecimal.ZERO;

        if (btcBalance == null) {
            accountBalanceMapper.insertBalance(buyerAccountId, baseAsset);
        }

        accountBalanceMapper.addAvailableBalance(buyerAccountId, baseAsset, tradeQuantity);

        BigDecimal afterBtcAvailable = beforeBtcAvailable.add(tradeQuantity);

        // 流水：买方获得 BTC
        insertLedger(buyerAccountId, baseAsset, "TRADE_BUY",
                result.getTradeNo() + "_BUY",
                tradeQuantity, BigDecimal.ZERO,
                beforeBtcAvailable, afterBtcAvailable,
                beforeBtcFrozen, beforeBtcFrozen);
    }

    /**
     * 卖方结算：
     * <ul>
     *   <li>扣除 BTC 冻结</li>
     *   <li>增加 USDT 可用余额</li>
     *   <li>扣除卖方手续费</li>
     * </ul>
     */
    private void settleSeller(MatchResult result, SymbolPairVO symbolPair) {
        Long sellerAccountId = result.getSellAccountId();
        String quoteAsset = symbolPair.getQuoteAsset(); // USDT
        String baseAsset = symbolPair.getBaseAsset();   // BTC

        BigDecimal tradeAmount = result.getAmount();
        BigDecimal tradeQuantity = result.getQuantity();
        BigDecimal sellFee = result.getSellFee();

        // 3a. 扣除 BTC 冻结
        AccountBalanceVO btcBalance = accountBalanceMapper.selectByAccountIdAndAsset(sellerAccountId, baseAsset);
        if (btcBalance == null) {
            log.error("卖方 BTC 余额记录不存在: accountId={}", sellerAccountId);
            return;
        }

        BigDecimal beforeBtcFrozen = btcBalance.getFrozenBalance();
        BigDecimal afterBtcFrozen = beforeBtcFrozen.subtract(tradeQuantity);
        int rows = accountBalanceMapper.subtractFrozenBalance(sellerAccountId, baseAsset, tradeQuantity);
        if (rows != 1) {
            log.error("卖方 BTC 冻结扣减失败: accountId={}, amount={}", sellerAccountId, tradeQuantity);
            return;
        }

        // 流水：卖方扣除 BTC 冻结
        insertLedger(sellerAccountId, baseAsset, "TRADE_SELL",
                result.getTradeNo() + "_SELL",
                BigDecimal.ZERO, tradeQuantity.negate(),
                btcBalance.getAvailableBalance(), btcBalance.getAvailableBalance(),
                beforeBtcFrozen, afterBtcFrozen);

        // 3b. 增加 USDT 可用余额
        AccountBalanceVO usdtBalance = accountBalanceMapper.selectByAccountIdAndAsset(sellerAccountId, quoteAsset);
        BigDecimal beforeUsdtAvailable = (usdtBalance != null) ? usdtBalance.getAvailableBalance() : BigDecimal.ZERO;
        BigDecimal beforeUsdtFrozen = (usdtBalance != null) ? usdtBalance.getFrozenBalance() : BigDecimal.ZERO;

        if (usdtBalance == null) {
            accountBalanceMapper.insertBalance(sellerAccountId, quoteAsset);
        }

        accountBalanceMapper.addAvailableBalance(sellerAccountId, quoteAsset, tradeAmount);
        BigDecimal afterUsdtAvailable = beforeUsdtAvailable.add(tradeAmount);

        // 流水：卖方获得 USDT
        insertLedger(sellerAccountId, quoteAsset, "TRADE_SELL_RECEIVE",
                result.getTradeNo() + "_SELL_RECEIVE",
                tradeAmount, BigDecimal.ZERO,
                beforeUsdtAvailable, afterUsdtAvailable,
                beforeUsdtFrozen, beforeUsdtFrozen);

        // 扣除卖方手续费（BTC）
        if (sellFee.compareTo(BigDecimal.ZERO) > 0) {
            AccountBalanceVO sellerBtcAfter = accountBalanceMapper.selectByAccountIdAndAsset(sellerAccountId, baseAsset);
            BigDecimal beforeFeeAvailable = (sellerBtcAfter != null) ? sellerBtcAfter.getAvailableBalance() : BigDecimal.ZERO;
            BigDecimal afterFeeAvailable = beforeFeeAvailable.subtract(sellFee);

            rows = accountBalanceMapper.subtractAvailableBalance(sellerAccountId, baseAsset, sellFee);
            if (rows != 1) {
                log.error("卖方手续费扣减失败: accountId={}, fee={}", sellerAccountId, sellFee);
            } else {
                // 流水：卖方手续费
                insertLedger(sellerAccountId, baseAsset, "FEE",
                        result.getTradeNo() + "_SELL_FEE",
                        sellFee.negate(), BigDecimal.ZERO,
                        beforeFeeAvailable, afterFeeAvailable,
                        afterBtcFrozen, afterBtcFrozen);
            }
        }
    }

    /**
     * 成交后增量更新订单已成交数量并判断订单状态。
     * <p>
     * 使用 {@link OrderMapper#incrementFilledQuantity} 以 SQL 增量方式更新，
     * 确保撮合过程中多笔成交的 filledQuantity 正确累加（而非从 DB 读取旧值后覆盖）。
     *
     * @param orderId       订单 ID
     * @param orderNo       订单号
     * @param tradeQuantity 本次成交数量（增量）
     */
    private void updateOrderAfterTrade(Long orderId, String orderNo, BigDecimal tradeQuantity) {
        // 1. 增量更新已成交数量（原子操作）
        int rows = orderMapper.incrementFilledQuantity(orderId, tradeQuantity);
        if (rows != 1) {
            log.error("订单已成交数量更新失败: orderId={}", orderId);
            return;
        }

        // 2. 重新查询订单最新状态
        OrderVO order = orderMapper.selectById(orderId);
        if (order == null) {
            log.error("订单不存在: orderId={}", orderId);
            return;
        }

        BigDecimal filledQty = order.getFilledQuantity();
        BigDecimal totalQty = order.getQuantity();

        // 3. 更新订单状态
        if (filledQty.compareTo(totalQty) >= 0) {
            orderMapper.updateStatus(orderId, "FILLED");
            log.info("订单完全成交: orderId={}, orderNo={}", orderId, orderNo);
        } else if (filledQty.compareTo(BigDecimal.ZERO) > 0) {
            orderMapper.updateStatus(orderId, "PARTIALLY_FILLED");
            log.info("订单部分成交: orderId={}, orderNo={}, filled={}/{}",
                    orderId, orderNo, filledQty, totalQty);
        }
    }

    /**
     * 插入资产流水记录。
     */
    private void insertLedger(Long accountId, String assetSymbol, String businessType,
                              String businessId, BigDecimal changeAvailable, BigDecimal changeFrozen,
                              BigDecimal beforeAvailable, BigDecimal afterAvailable,
                              BigDecimal beforeFrozen, BigDecimal afterFrozen) {
        AssetLedgerRecord record = new AssetLedgerRecord();
        record.setAccountId(accountId);
        record.setAssetSymbol(assetSymbol);
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setChangeAvailable(changeAvailable);
        record.setChangeFrozen(changeFrozen);
        record.setBeforeAvailable(beforeAvailable);
        record.setAfterAvailable(afterAvailable);
        record.setBeforeFrozen(beforeFrozen);
        record.setAfterFrozen(afterFrozen);
        assetLedgerMapper.insert(record);
    }

    /**
     * 生成订单号：ORD + yyyyMMddHHmmss + 6位序列
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long seq = ORDER_SEQ.getAndIncrement();
        // 每毫秒最多 999999 个订单，超出时重置（实际生产中应使用分布式ID）
        if (seq > 999999) {
            ORDER_SEQ.set(1);
            seq = 1;
        }
        return ORDER_NO_PREFIX + timestamp + String.format("%06d", seq);
    }
}
