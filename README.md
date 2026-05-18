# Mini-CEX

> **Mini Centralized Exchange** — 一个现货交易所后端模拟系统。项目完整实现了交易所核心后端流程：账户、资产余额、交易对、订单、撮合、成交、资金流水、行情查询、钱包充值提现等。


## 📋 目录

- [项目概述](#项目概述)
- [技术栈](#技术栈)
- [已实现功能](#已实现功能)
- [架构说明](#架构说明)
- [快速开始](#快速开始)
- [API 文档](#api-文档)
- [核心设计亮点](#核心设计亮点)
- [数据库表结构](#数据库表结构)
- [项目结构](#项目结构)
- [下一步计划](#下一步计划)


## 项目概述

Mini-CEX 是一个学习/面试导向的交易所后端系统，旨在模拟真实中心化交易所（CEX）的核心业务流程：

- 用户注册登录 → 账户管理 → 模拟充值 → 下单交易 → 撮合成交 → 行情展示 → 钱包充提


## 技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Java | 17 | 开发语言 |
| Spring Boot | 4.0.6 | 应用框架 |
| MyBatis | 4.0.1 | ORM / 数据访问 |
| MySQL | 8.x | 数据库 |
| JWT (jjwt) | 0.12.6 | 认证令牌 |
| Spring Security Crypto | - | BCrypt 密码加密 |
| SpringDoc OpenAPI | 3.0.3 | API 文档 / Swagger UI |
| Lombok | - | 代码简化 |
| Maven | - | 构建工具 |

---

## 已实现功能

### ✅ V0：基础框架

| 功能 | 说明 | 位置 |
|---|---|---|
| 统一响应格式 | `ApiResponse<T>` 封装 code/message/data/traceId | [`ApiResponse.java`](src/main/java/com/fffg/cex/common/result/ApiResponse.java) |
| 全局异常处理 | 统一处理业务异常、参数校验异常、唯一约束冲突 | [`GlobalExceptionHandler.java`](src/main/java/com/fffg/cex/common/exception/GlobalExceptionHandler.java) |
| 错误码枚举 | 标准化错误码体系 | [`ErrorCode.java`](src/main/java/com/fffg/cex/common/exception/ErrorCode.java) |
| 参数校验 | `@Valid` + `@NotBlank` / `@DecimalMin` 等注解 | 各 DTO 层 |
| 请求追踪 | `X-Trace-Id` 自定义/自动生成 | [`TraceIdFilter.java`](src/main/java/com/fffg/cex/common/filter/TraceIdFilter.java) |
| Swagger UI | `/api/swagger-ui/index.html` | SpringDoc 自动配置 |
| 健康检查 | `GET /api/health` | [`HealthController.java`](src/main/java/com/fffg/cex/common/controller/HealthController.java) |

### ✅ V0.5：认证模块

| 功能 | 接口 | 说明 |
|---|---|---|
| 用户注册 | `POST /api/auth/register` | BCrypt 密码加密 + 注册后自动返回 JWT |
| 用户登录 | `POST /api/auth/login` | 密码校验 + 生成 JWT Token |
| JWT 鉴权 | 全局过滤器 | 所有请求经过 `JwtAuthFilter` 拦截校验 |

### ✅ V1：市场基础模块

| 功能 | 接口 | 说明 |
|---|---|---|
| 查询支持币种 | `GET /api/markets/assets` | 返回 USDT、BTC、ETH 等币种信息 |
| 查询交易对 | `GET /api/markets/symbols` | 返回 BTCUSDT、ETHUSDT 等交易对信息 |

### ✅ V1：账户与资产模块

| 功能 | 接口 | 说明 |
|---|---|---|
| 创建账户 | `POST /api/accounts` | 自动初始化 USDT/BTC/ETH 余额为 0 |
| 查询账户详情 | `GET /api/accounts/{accountId}` | - |
| 查询资产余额 | `GET /api/accounts/{accountId}/balances` | 双余额模型（可用 + 冻结） |
| 模拟充值 | `POST /api/accounts/{accountId}/balances/deposit` | 含幂等控制、精度校验、流水记录 |
| 查询资金流水 | `GET /api/accounts/{accountId}/ledgers` | 分页查询资产变化历史 |

### ✅ V2：订单模块

| 功能 | 接口 | 说明 |
|---|---|---|
| 创建限价单 | `POST /api/orders` | 精度校验、最小金额校验、冻结资产、触发撮合 |
| 查询订单详情 | `GET /api/orders/{orderId}` | - |
| 查询账户订单列表 | `GET /api/accounts/{accountId}/orders` | 分页 + 多种筛选条件 |
| 撤销订单 | `POST /api/orders/{orderId}/cancel` | 解冻剩余资产、更新状态、移除订单簿 |

### ✅ V3：撮合与成交模块

| 功能 | 说明 | 位置 |
|---|---|---|
| 内存订单簿 | TreeMap 实现，bids 降序/asks 升序，同价时间优先 | [`OrderBook.java`](src/main/java/com/fffg/cex/matching/OrderBook.java) |
| 撮合引擎 | 价格优先 + 时间优先 + maker 价格成交 + 价差退款 | [`MatchEngine.java`](src/main/java/com/fffg/cex/matching/MatchEngine.java) |
| 交易对互斥锁 | `ReentrantLock` 按 symbol 隔离，确保并发安全 | [`OrderBookManager.java`](src/main/java/com/fffg/cex/matching/OrderBookManager.java) |
| 启动恢复 | 启动时从 DB 加载活跃订单到内存 | [`OrderBookManager.java`](src/main/java/com/fffg/cex/matching/OrderBookManager.java#L42) |
| 成交结算 | 买方：扣 USDT 冻结→得 BTC→价差退款→手续费；卖方：扣 BTC 冻结→得 USDT→手续费 | [`OrderServiceImpl.java`](src/main/java/com/fffg/cex/order/service/Impl/OrderServiceImpl.java#L345) |
| 查询成交记录 | `GET /api/trades?symbol=BTCUSDT` | [`TradeServiceImpl.java`](src/main/java/com/fffg/cex/trade/service/Impl/TradeServiceImpl.java) |

### ✅ V4：行情模块

| 功能 | 接口 | 说明 |
|---|---|---|
| 订单簿深度 | `GET /api/market/depth` | 从内存 OrderBook 直接读取快照 |
| 最新成交（行情） | `GET /api/market/trades` | 精简版成交数据 |
| 24h Ticker | `GET /api/market/ticker` | 含缓存，每 10 秒刷新 |
| K 线数据 | `GET /api/market/klines` | SQL 聚合，支持 1m/5m/15m/30m/1h/4h/1d/1w |
| Ticker 缓存 | `TickerCache` | ConcurrentHashMap + `@Scheduled(fixedRate=10000)` |
| K 线聚合器 | `KlineAggregator` | 每分钟定时聚合到内存缓存 |

### ✅ V5：钱包模块

| 功能 | 接口 | 说明 |
|---|---|---|
| 获取充值地址 | `GET /api/wallet/deposit-address` | 模拟生成，同账户同币种同链固定 |
| 查询充值记录 | `GET /api/wallet/deposits` | 支持状态、币种筛选 |
| 提现申请 | `POST /api/wallet/withdraws` | 冻结资产、大额自动审核、小额自动通过 |
| 查询提现记录 | `GET /api/wallet/withdraws` | 支持状态筛选 |
| 审核通过提现 | `POST /api/admin/withdraws/{id}/approve` | 管理端接口 |
| 审核拒绝提现 | `POST /api/admin/withdraws/{id}/reject` | 解冻资产 + 流水 |
| 充值确认模拟 | 定时任务每 5 秒 | 确认数递增 → 自动到账 |
| 提现完成模拟 | 定时任务每 30 秒 | APPROVED → COMPLETED + 扣冻结 + 模拟 txHash |

---

## 架构说明

### 分层架构

```
Controller → Service (Impl) → Mapper → MySQL
                ↕
           MatchEngine (内存撮合)
                ↕
           OrderBook (内存订单簿)
```

### 核心业务流程

```
用户注册/登录
    ↓
模拟充值 (USDT/BTC/ETH)
    ↓
下买单 (冻结 USDT) / 下卖单 (冻结 BTC)
    ↓
撮合引擎匹配 (内存订单簿)
    ↓
成交结算 (冻结扣除 + 资产划转 + 价差退款 + 手续费)
    ↓
行情展示 (深度/Ticker/K线)
    ↓
提现申请 → 审核 → 链上模拟完成
```

### 双余额模型

| 字段 | 说明 |
|---|---|
| `availableBalance` | 可用余额，可下单或提现 |
| `frozenBalance` | 冻结余额，已被订单或提现占用 |

核心原则：
- **下单**：`available -= X`，`frozen += X`
- **成交**：`frozen -= X`，对方 `available += Y`
- **撤单**：`frozen -= X`，`available += X`
- **价差退款**：买方多冻结的部分退还 `available`

### 并发安全策略

- **按交易对加锁**：每个 symbol 一个 `ReentrantLock`，下单、撤单、撮合三者互斥
- **条件更新**：冻结资产使用 SQL `WHERE available_balance >= ?` 防止超扣
- **原子增量**：订单已成交数量使用 `UPDATE ... SET filled_quantity = filled_quantity + ?` 原子操作

### 撮合规则

```
买单：价格高优先 → 时间早优先
卖单：价格低优先 → 时间早优先
成交价 = maker 价格（先挂单方）
买价 >= 卖价 时成交
```

### 资金流水（审计）

- `account_balance`：当前余额快照表
- `asset_ledger`：资产变化历史表（只插入，不更新，不删除）
- 任何资产变化均记录流水（含变化前后余额快照），支持问题排查和回放审计

---

## 快速开始

### 前置条件

- JDK 17+
- MySQL 8.x
- Maven（或使用项目自带的 `mvnw`）

### 1. 创建数据库

```sql
CREATE DATABASE mini_cex DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 初始化表结构

执行设计文档中的建表 SQL（参见 [`mini_cex_api_design_doc.md`](mini_cex_api_design_doc.md#2757)），包含以下表：

- `account` — 账户表
- `auth_account` — 认证账户表（含密码）
- `asset` — 币种表
- `symbol_pair` — 交易对表
- `account_balance` — 账户余额表
- `asset_ledger` — 资产流水表
- `trade_order` — 订单表
- `trade_fill` — 成交记录表
- `deposit_address` — 充值地址表
- `deposit_record` — 充值记录表
- `withdraw_record` — 提现记录表

### 3. 初始化基础数据

```sql
INSERT INTO asset (symbol, name, scale_num, status, created_at) VALUES
('USDT', 'Tether USD', 6, 1, NOW()),
('BTC', 'Bitcoin', 8, 1, NOW()),
('ETH', 'Ethereum', 8, 1, NOW());

INSERT INTO symbol_pair (symbol, base_asset, quote_asset, price_scale, quantity_scale, min_order_amount, status, created_at) VALUES
('BTCUSDT', 'BTC', 'USDT', 2, 8, 10, 1, NOW()),
('ETHUSDT', 'ETH', 'USDT', 2, 8, 10, 1, NOW());
```

### 4. 配置数据库连接

编辑 [`src/main/resources/application.yml`](src/main/resources/application.yml)，配置 `spring.datasource`（当前使用 `local` profile）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mini_cex?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 5. 启动项目

```bash
# 使用 Maven Wrapper
./mvnw spring-boot:run

# 或打包后运行
./mvnw clean package -DskipTests
java -jar target/cex-0.0.1-SNAPSHOT.jar
```

### 6. 验证

```bash
curl http://localhost:8080/api/health
```

访问 Swagger UI: http://localhost:8080/api/swagger-ui/index.html

---

## API 文档

启动项目后访问 Swagger UI：

- http://localhost:8080/api/swagger-ui/index.html
- http://localhost:8080/api/v3/api-docs

### 接口总览

| 方法 | 路径 | 说明 | 模块 |
|---|---|---|---|
| GET | `/api/health` | 健康检查 | Common |
| POST | `/api/auth/register` | 用户注册 | Auth |
| POST | `/api/auth/login` | 用户登录 | Auth |
| GET | `/api/markets/assets` | 查询支持币种 | Market |
| GET | `/api/markets/symbols` | 查询交易对 | Market |
| POST | `/api/accounts` | 创建账户 | Account |
| GET | `/api/accounts/{accountId}` | 查询账户 | Account |
| GET | `/api/accounts/{accountId}/balances` | 查询余额 | Account |
| POST | `/api/accounts/{accountId}/balances/deposit` | 模拟充值 | Account |
| GET | `/api/accounts/{accountId}/ledgers` | 查询资金流水 | Account |
| POST | `/api/orders` | 创建订单 | Order |
| GET | `/api/orders/{orderId}` | 查询订单 | Order |
| GET | `/api/accounts/{accountId}/orders` | 查询账户订单列表 | Order |
| POST | `/api/orders/{orderId}/cancel` | 撤销订单 | Order |
| GET | `/api/trades` | 查询成交记录 | Trade |
| GET | `/api/market/depth` | 查询订单簿深度 | Market Data |
| GET | `/api/market/trades` | 查询最新成交 | Market Data |
| GET | `/api/market/ticker` | 查询 24h Ticker | Market Data |
| GET | `/api/market/klines` | 查询 K 线 | Market Data |
| GET | `/api/wallet/deposit-address` | 获取充值地址 | Wallet |
| GET | `/api/wallet/deposits` | 查询充值记录 | Wallet |
| POST | `/api/wallet/withdraws` | 提现申请 | Wallet |
| GET | `/api/wallet/withdraws` | 查询提现记录 | Wallet |
| POST | `/api/admin/withdraws/{id}/approve` | 审核通过提现 | Admin |
| POST | `/api/admin/withdraws/{id}/reject` | 审核拒绝提现 | Admin |

---

## 核心设计亮点

### 1. 双余额模型

```
availableBalance + frozenBalance
```

下单时先冻结，成交时扣冻结，撤单时解冻。价差部分自动退还可用余额。

### 2. 条件更新防止超扣

```sql
UPDATE account_balance
SET available_balance = available_balance - ?,
    frozen_balance = frozen_balance + ?
WHERE account_id = ? AND asset_symbol = ? AND available_balance >= ?;
```

通过 `WHERE available_balance >= ?` 确保不会发生余额透支，然后判断影响行数是否为 1。

### 3. 完整的资金流水审计

每次资产变化都记录 `asset_ledger` 流水，包含变化前后的余额快照。支持问题排查和历史回放。

### 4. 内存订单簿 + 启动恢复

- 订单簿使用 `TreeMap`（红黑树），买盘降序、卖盘升序
- 服务启动时自动从数据库加载未成交订单
- 按交易对独立加锁，不同交易对互不阻塞

### 5. 撮合引擎

- **价格优先**：买单高价优先，卖单低价优先
- **时间优先**：同价格先到先得
- **Maker 价格成交**：成交价取先挂单价格
- **价差退款**：taker 多冻结的差额自动退还

### 6. Ticker/K 线缓存

- Ticker 每 10 秒定时刷新到 `ConcurrentHashMap` 缓存
- K 线每分钟定时从成交数据聚合到内存

### 7. 钱包模拟

- 充值地址：按 `accountId:assetSymbol:chain` 哈希生成，固定不变
- 充值确认：定时任务每 5 秒递增确认数，到账后自动增加余额
- 提现流程：申请 → 冻结 → 审核（大额人工/小额自动）→ 链上完成模拟

---

## 数据库表结构

| 表名 | 说明 | 核心字段 |
|---|---|---|
| `account` | 账户表 | id, username, status |
| `auth_account` | 认证账户表 | id, account_id, password_hash |
| `asset` | 币种表 | symbol, name, scale_num, status |
| `symbol_pair` | 交易对表 | symbol, base_asset, quote_asset, price_scale, quantity_scale, min_order_amount |
| `account_balance` | 账户余额表 | account_id, asset_symbol, available_balance, frozen_balance |
| `asset_ledger` | 资产流水表 | account_id, asset_symbol, business_type, business_id, change_available, change_frozen, before/after |
| `trade_order` | 订单表 | order_no, account_id, symbol, side, order_type, price, quantity, filled_quantity, status |
| `trade_fill` | 成交记录表 | trade_no, symbol, buy_order_id, sell_order_id, price, quantity, amount, buy_fee, sell_fee |
| `deposit_address` | 充值地址表 | account_id, asset_symbol, chain, address |
| `deposit_record` | 充值记录表 | account_id, asset_symbol, chain, tx_hash, amount, confirmations, status |
| `withdraw_record` | 提现记录表 | account_id, asset_symbol, chain, to_address, amount, fee, status, tx_hash |

---

## 项目结构

```
src/main/java/com/fffg/cex/
├── CexApplication.java                  # 启动类
├── auth/                                # 认证模块
│   ├── config/JwtProperties.java
│   ├── controller/AuthController.java
│   ├── dto/LoginRequestDTO.java, RegisterRequestDTO.java
│   ├── filter/JwtAuthFilter.java
│   ├── mapper/AuthAccountMapper.java
│   ├── service/Impl/AuthServiceImpl.java
│   ├── util/JwtUtil.java
│   └── vo/LoginVO.java
├── account/                             # 账户模块
│   ├── Controller/AccountController.java
│   ├── DTO/CreateAccountRequestDTO.java, DepositRequestDTO.java
│   ├── Mapper/AccountMapper.java, AccountBalanceMapper.java, AssetLedgerMapper.java
│   ├── Service/Impl/AccountServiceImpl.java
│   └── VO/AccountVO.java, AccountBalanceVO.java, AssetLedgerVO.java, PageVO.java
├── order/                               # 订单模块
│   ├── controller/OrderController.java
│   ├── dto/CreateOrderRequestDTO.java
│   ├── mapper/OrderMapper.java
│   ├── service/Impl/OrderServiceImpl.java
│   └── vo/OrderVO.java, OrderCancelVO.java
├── matching/                            # 撮合引擎
│   ├── MatchEngine.java                 # 撮合引擎核心逻辑
│   ├── MatchResult.java                 # 撮合结果
│   ├── OrderBook.java                   # 内存订单簿
│   └── OrderBookManager.java            # 订单簿管理器 + 锁管理
├── trade/                               # 成交模块
│   ├── controller/TradeController.java
│   ├── mapper/TradeMapper.java
│   ├── service/Impl/TradeServiceImpl.java
│   └── vo/TradeVO.java
├── market/                              # 市场基础模块
│   ├── Controller/MarketController.java
│   ├── Mapper/AssetsMapper.java, SymbolPairMapper.java
│   ├── Service/Impl/AssetsServiceImpl.java, SymbolPairServiceImpl.java
│   └── VO/AssetVO.java, SymbolPairVO.java
├── marketdata/                          # 行情模块
│   ├── cache/TickerCache.java, KlineAggregator.java
│   ├── controller/MarketDataController.java
│   ├── mapper/MarketDataMapper.java
│   ├── service/Impl/MarketDataServiceImpl.java
│   └── vo/KlineVO.java, MarketDepthVO.java, MarketTradeVO.java, TickerVO.java
├── wallet/                              # 钱包模块
│   ├── controller/WalletController.java, AdminWalletController.java
│   ├── dto/WithdrawRequestDTO.java
│   ├── manager/DepositAddressManager.java
│   ├── mapper/DepositRecordMapper.java, WithdrawRecordMapper.java
│   ├── scheduler/DepositConfirmationSimulator.java, WithdrawCompletionSimulator.java
│   ├── service/Impl/WalletServiceImpl.java
│   └── vo/DepositAddressVO.java, DepositRecordVO.java, WithdrawRecordVO.java, WithdrawResultVO.java
└── common/                              # 通用模块
    ├── config/WebMvcConfig.java
    ├── controller/HealthController.java
    ├── exception/BusinessException.java, ErrorCode.java, GlobalExceptionHandler.java
    ├── filter/TraceIdFilter.java
    └── result/ApiResponse.java
```

## License

MIT
