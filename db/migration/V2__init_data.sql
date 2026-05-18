-- ============================================================
-- Flyway Migration: V2 - 初始化基础数据
-- ============================================================

-- 1. 币种配置
INSERT INTO `asset` (`symbol`, `name`, `scale_num`, `status`) VALUES
('BTC',  'Bitcoin',          8, 1),
('ETH',  'Ethereum',         8, 1),
('USDT', 'Tether USD',       2, 1),
('BNB',  'Binance Coin',     8, 1),
('SOL',  'Solana',           8, 1);

-- 2. 交易对配置
INSERT INTO `symbol_pair` (`symbol`, `base_asset`, `quote_asset`, `price_scale`, `quantity_scale`, `min_order_amount`, `status`) VALUES
('BTCUSDT',  'BTC',  'USDT', 2, 6, 10.00, 1),
('ETHUSDT',  'ETH',  'USDT', 2, 4, 10.00, 1),
('BNBUSDT',  'BNB',  'USDT', 2, 4, 10.00, 1),
('SOLUSDT',  'SOL',  'USDT', 2, 2, 10.00, 1);
