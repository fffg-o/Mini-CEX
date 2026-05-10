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
import com.fffg.cex.order.dto.CreateOrderRequestDTO;
import com.fffg.cex.order.mapper.OrderMapper;
import com.fffg.cex.order.service.OrderService;
import com.fffg.cex.order.vo.OrderCancelVO;
import com.fffg.cex.order.vo.OrderVO;
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

        // 9. 插入资产流水
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

        // 10. 返回完整的订单VO
        return orderMapper.selectById(orderId);
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

        // 2. 校验订单状态
        String status = order.getStatus();
        if ("FILLED".equals(status)) {
            throw new BusinessException(ErrorCode.ORDER_FULLY_FILLED.getCode(),
                    ErrorCode.ORDER_FULLY_FILLED.getMessage());
        }
        if ("CANCELED".equals(status)) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELED.getCode(),
                    ErrorCode.ORDER_ALREADY_CANCELED.getMessage());
        }

        // 3. 计算剩余未成交数量
        BigDecimal remainingQuantity = order.getQuantity().subtract(order.getFilledQuantity());

        // 4. 查询交易对信息，确定解冻资产类型和数量
        SymbolPairVO symbolPair = symbolPairMapper.selectBySymbol(order.getSymbol());
        if (symbolPair == null) {
            throw new BusinessException(ErrorCode.SYMBOL_NOT_FOUND.getCode(),
                    ErrorCode.SYMBOL_NOT_FOUND.getMessage());
        }

        String unfreezeAsset;
        BigDecimal unfreezeAmount;

        if ("BUY".equals(order.getSide())) {
            // 买单解冻 USDT：price * remainingQuantity
            unfreezeAsset = symbolPair.getQuoteAsset();
            unfreezeAmount = order.getPrice().multiply(remainingQuantity)
                    .setScale(symbolPair.getPriceScale(), RoundingMode.DOWN);
        } else {
            // 卖单解冻 BTC：remainingQuantity
            unfreezeAsset = symbolPair.getBaseAsset();
            unfreezeAmount = remainingQuantity;
        }

        // 5. 查询当前余额（用于流水记录）
        AccountBalanceVO balance = accountBalanceMapper.selectByAccountIdAndAsset(
                order.getAccountId(), unfreezeAsset);
        BigDecimal beforeAvailable = (balance != null) ? balance.getAvailableBalance() : BigDecimal.ZERO;
        BigDecimal beforeFrozen = (balance != null) ? balance.getFrozenBalance() : BigDecimal.ZERO;

        // 6. 条件更新解冻资产
        int updatedRows = accountBalanceMapper.unfreezeBalance(
                order.getAccountId(), unfreezeAsset, unfreezeAmount);
        if (updatedRows != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(),
                    "解冻资产失败，冻结余额不足");
        }

        BigDecimal afterAvailable = beforeAvailable.add(unfreezeAmount);
        BigDecimal afterFrozen = beforeFrozen.subtract(unfreezeAmount);

        // 7. 更新订单状态为 CANCELED
        orderMapper.updateStatus(orderId, "CANCELED");

        // 8. 插入资产流水
        AssetLedgerRecord ledger = new AssetLedgerRecord();
        ledger.setAccountId(order.getAccountId());
        ledger.setAssetSymbol(unfreezeAsset);
        ledger.setBusinessType("ORDER_UNFREEZE");
        ledger.setBusinessId(order.getOrderNo());
        ledger.setChangeAvailable(unfreezeAmount);
        ledger.setChangeFrozen(unfreezeAmount.negate());
        ledger.setBeforeAvailable(beforeAvailable);
        ledger.setAfterAvailable(afterAvailable);
        ledger.setBeforeFrozen(beforeFrozen);
        ledger.setAfterFrozen(afterFrozen);
        assetLedgerMapper.insert(ledger);

        return new OrderCancelVO(orderId, "CANCELED");
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
