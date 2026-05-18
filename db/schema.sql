-- ============================================================
-- Mini-CEX 数据库完整建表脚本
-- 适用于 MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS mini_cex DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mini_cex;

-- ============================================================
-- 1. 账户表
-- ============================================================
DROP TABLE IF EXISTS `account`;
CREATE TABLE `account` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '账户ID',
    `username`     VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(255) DEFAULT NULL COMMENT '密码哈希（认证模块使用）',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户表';

-- ============================================================
-- 2. 账户余额表
-- ============================================================
DROP TABLE IF EXISTS `account_balance`;
CREATE TABLE `account_balance` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `account_id`        BIGINT       NOT NULL COMMENT '账户ID',
    `asset_symbol`      VARCHAR(32)  NOT NULL COMMENT '币种标识',
    `available_balance` DECIMAL(40,8) NOT NULL DEFAULT 0 COMMENT '可用余额',
    `frozen_balance`    DECIMAL(40,8) NOT NULL DEFAULT 0 COMMENT '冻结余额',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_asset` (`account_id`, `asset_symbol`),
    KEY `idx_account_id` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户余额表';

-- ============================================================
-- 3. 资产流水表
-- ============================================================
DROP TABLE IF EXISTS `asset_ledger`;
CREATE TABLE `asset_ledger` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `account_id`       BIGINT       NOT NULL COMMENT '账户ID',
    `asset_symbol`     VARCHAR(32)  NOT NULL COMMENT '币种标识',
    `business_type`    VARCHAR(64)  NOT NULL COMMENT '业务类型：ORDER_FREEZE/ORDER_UNFREEZE/TRADE_BUY/TRADE_SELL/FEE/MOCK_DEPOSIT/TRADE_REFUND/WITHDRAW_FREEZE/WITHDRAW_UNFREEZE等',
    `business_id`      VARCHAR(128) NOT NULL COMMENT '业务ID（用于幂等性校验）',
    `change_available` DECIMAL(40,8) NOT NULL COMMENT '可用余额变化（正数增加，负数减少）',
    `change_frozen`    DECIMAL(40,8) NOT NULL COMMENT '冻结余额变化',
    `before_available` DECIMAL(40,8) NOT NULL COMMENT '变化前可用余额',
    `after_available`  DECIMAL(40,8) NOT NULL COMMENT '变化后可用余额',
    `before_frozen`    DECIMAL(40,8) NOT NULL COMMENT '变化前冻结余额',
    `after_frozen`     DECIMAL(40,8) NOT NULL COMMENT '变化后冻结余额',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_account_asset` (`account_id`, `asset_symbol`),
    KEY `idx_business_id` (`business_id`),
    KEY `idx_business_type` (`business_type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产流水表';

-- ============================================================
-- 4. 币种配置表
-- ============================================================
DROP TABLE IF EXISTS `asset`;
CREATE TABLE `asset` (
    `symbol`    VARCHAR(32) NOT NULL COMMENT '币种标识',
    `name`      VARCHAR(64) NOT NULL COMMENT '币种名称',
    `scale_num` INT         NOT NULL DEFAULT 8 COMMENT '小数精度',
    `status`    TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    PRIMARY KEY (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='币种配置表';

-- ============================================================
-- 5. 交易对配置表
-- ============================================================
DROP TABLE IF EXISTS `symbol_pair`;
CREATE TABLE `symbol_pair` (
    `symbol`          VARCHAR(32)   NOT NULL COMMENT '交易对标识，如 BTCUSDT',
    `base_asset`      VARCHAR(32)   NOT NULL COMMENT '基础币种，如 BTC',
    `quote_asset`     VARCHAR(32)   NOT NULL COMMENT '计价币种，如 USDT',
    `price_scale`     INT           NOT NULL DEFAULT 2 COMMENT '价格精度（小数位数）',
    `quantity_scale`  INT           NOT NULL DEFAULT 6 COMMENT '数量精度（小数位数）',
    `min_order_amount` DECIMAL(40,8) NOT NULL DEFAULT 0 COMMENT '最小交易金额（计价币种）',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易对配置表';

-- ============================================================
-- 6. 订单表
-- ============================================================
DROP TABLE IF EXISTS `trade_order`;
CREATE TABLE `trade_order` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`        VARCHAR(64)   NOT NULL COMMENT '订单号',
    `account_id`      BIGINT        NOT NULL COMMENT '账户ID',
    `symbol`          VARCHAR(32)   NOT NULL COMMENT '交易对',
    `side`            VARCHAR(8)    NOT NULL COMMENT '方向：BUY/SELL',
    `order_type`      VARCHAR(16)   NOT NULL DEFAULT 'LIMIT' COMMENT '订单类型：LIMIT',
    `price`           DECIMAL(40,8) NOT NULL COMMENT '价格',
    `quantity`        DECIMAL(40,8) NOT NULL COMMENT '原始数量',
    `filled_quantity` DECIMAL(40,8) NOT NULL DEFAULT 0 COMMENT '已成交数量',
    `status`          VARCHAR(32)   NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW/PARTIALLY_FILLED/FILLED/CANCELED',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_symbol` (`symbol`),
    KEY `idx_account_symbol` (`account_id`, `symbol`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================================
-- 7. 成交记录表
-- ============================================================
DROP TABLE IF EXISTS `trade_fill`;
CREATE TABLE `trade_fill` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '成交ID',
    `trade_no`        VARCHAR(64)   NOT NULL COMMENT '成交编号',
    `symbol`          VARCHAR(32)   NOT NULL COMMENT '交易对',
    `buy_order_id`    BIGINT        NOT NULL COMMENT '买单ID',
    `sell_order_id`   BIGINT        NOT NULL COMMENT '卖单ID',
    `buy_account_id`  BIGINT        NOT NULL COMMENT '买方账户ID',
    `sell_account_id` BIGINT        NOT NULL COMMENT '卖方账户ID',
    `price`           DECIMAL(40,8) NOT NULL COMMENT '成交价格',
    `quantity`        DECIMAL(40,8) NOT NULL COMMENT '成交数量',
    `amount`          DECIMAL(40,8) NOT NULL COMMENT '成交金额 = price * quantity',
    `buy_fee`         DECIMAL(40,8) NOT NULL DEFAULT 0 COMMENT '买方手续费',
    `sell_fee`        DECIMAL(40,8) NOT NULL DEFAULT 0 COMMENT '卖方手续费',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trade_no` (`trade_no`),
    KEY `idx_symbol` (`symbol`),
    KEY `idx_buy_order` (`buy_order_id`),
    KEY `idx_sell_order` (`sell_order_id`),
    KEY `idx_buy_account` (`buy_account_id`),
    KEY `idx_sell_account` (`sell_account_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成交记录表';

-- ============================================================
-- 8. 充值记录表
-- ============================================================
DROP TABLE IF EXISTS `deposit_record`;
CREATE TABLE `deposit_record` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `account_id`           BIGINT       NOT NULL COMMENT '账户ID',
    `asset_symbol`         VARCHAR(32)  NOT NULL COMMENT '币种标识',
    `chain`                VARCHAR(32)  NOT NULL COMMENT '链名称',
    `tx_hash`              VARCHAR(128) DEFAULT NULL COMMENT '交易哈希',
    `amount`               DECIMAL(40,8) NOT NULL COMMENT '充值金额',
    `confirmations`        INT          NOT NULL DEFAULT 0 COMMENT '当前确认数',
    `required_confirmations` INT        NOT NULL DEFAULT 12 COMMENT '要求确认数',
    `status`               VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/CONFIRMED/FAILED',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `confirmed_at`         DATETIME     DEFAULT NULL COMMENT '确认时间',
    PRIMARY KEY (`id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_status` (`status`),
    KEY `idx_tx_hash` (`tx_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值记录表';

-- ============================================================
-- 9. 提现记录表
-- ============================================================
DROP TABLE IF EXISTS `withdraw_record`;
CREATE TABLE `withdraw_record` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `account_id`   BIGINT        NOT NULL COMMENT '账户ID',
    `asset_symbol` VARCHAR(32)   NOT NULL COMMENT '币种标识',
    `chain`        VARCHAR(32)   NOT NULL COMMENT '链名称',
    `to_address`   VARCHAR(256)  NOT NULL COMMENT '提现地址',
    `amount`       DECIMAL(40,8) NOT NULL COMMENT '提现金额',
    `fee`          DECIMAL(40,8) NOT NULL DEFAULT 0 COMMENT '手续费',
    `status`       VARCHAR(32)   NOT NULL DEFAULT 'AUTO_APPROVED' COMMENT '状态：AUTO_APPROVED/REVIEWING/APPROVED/REJECTED/COMPLETED/FAILED',
    `business_id`  VARCHAR(128)  DEFAULT NULL COMMENT '业务ID（幂等）',
    `tx_hash`      VARCHAR(128)  DEFAULT NULL COMMENT '交易哈希',
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_business_id` (`business_id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现记录表';
