# Mini-CEX API 设计文档

> 项目定位：Mini-CEX 是一个现货交易所后端模拟系统，使用虚拟资产，不接入真实资金。项目重点实现账户、资产余额、交易对、订单、撮合、成交、资金流水、行情查询等交易所核心后端流程。

---

## 1. API 设计约定

### 1.1 基础路径

```text
Base URL: http://localhost:8080
API Prefix: /api
```

### 1.2 响应格式

所有接口统一返回 `ApiResponse<T>`。

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败响应：

```json
{
  "code": 40001,
  "message": "账户不存在",
  "data": null
}
```

### 1.3 金额与精度约定

交易所系统中所有金额、价格、数量字段统一使用 `BigDecimal`，接口 JSON 中建议用字符串传递，避免浮点精度问题。

示例：

```json
{
  "price": "60000.00",
  "quantity": "0.1",
  "amount": "6000.00"
}
```

### 1.4 命名约定

| 类型 | 示例 |
|---|---|
| 币种 symbol | `BTC`, `ETH`, `USDT` |
| 交易对 symbol | `BTCUSDT`, `ETHUSDT` |
| 订单方向 side | `BUY`, `SELL` |
| 订单类型 orderType | `LIMIT`, `MARKET`，第一阶段只做 `LIMIT` |
| 订单状态 status | `NEW`, `PARTIALLY_FILLED`, `FILLED`, `CANCELED`, `REJECTED` |
| 流水类型 businessType | `MOCK_DEPOSIT`, `ORDER_FREEZE`, `ORDER_UNFREEZE`, `TRADE_BUY`, `TRADE_SELL`, `FEE` |

---

## 2. 开发阶段划分

### V0：基础框架

目标：项目能启动，接口能访问，Swagger 能展示。

- 健康检查
- 统一返回结果
- 全局异常处理
- 参数校验
- Swagger / OpenAPI 文档

### V1：账户与资产模块

目标：完成交易系统地基。

- 创建账户
- 查询账户
- 查询资产余额
- 模拟充值
- 查询资金流水
- 查询币种
- 查询交易对

### V2：订单与资产冻结模块

目标：完成下单、撤单、余额冻结。

- 创建限价单
- 买单冻结 USDT
- 卖单冻结 BTC
- 查询订单
- 撤销订单
- 撤单解冻资产

### V3：撮合与成交模块

目标：完成交易闭环。

- 撮合引擎
- 成交记录
- 成交结算
- 更新订单状态
- 更新订单簿
- 生成交易流水

### V4：行情模块

目标：让系统更像真实交易所。

- 订单簿 depth
- 最新成交 trades
- ticker
- K 线
- WebSocket 推送

### V5：钱包与风控扩展

目标：面试加分项。

- 充值记录
- 提现申请
- 提现审核
- 提现冻结
- 链上 txHash 模拟
- 风控规则

---

## 3. 通用模块 API

## 3.1 健康检查

### GET `/api/health`

用于检查服务是否正常启动。

#### 请求参数

无。

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "UP",
    "system": "mini-cex"
  }
}
```

#### Controller 建议

```java
@GetMapping("/api/health")
public ApiResponse<Map<String, String>> health()
```

---

# 4. 市场基础模块 Market API

市场基础模块用于维护币种和交易对。第一阶段可以只做查询，数据通过 `data.sql` 或手动 SQL 初始化。

推荐包结构：

```text
com.fffg.cex.market
├── controller
├── service
├── mapper
├── entity
└── vo
```

---

## 4.1 查询支持币种

### GET `/api/markets/assets`

查询系统支持的币种列表，例如 USDT、BTC、ETH。

#### 请求参数

无。

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "symbol": "USDT",
      "name": "Tether USD",
      "scaleNum": 6,
      "status": 1
    },
    {
      "symbol": "BTC",
      "name": "Bitcoin",
      "scaleNum": 8,
      "status": 1
    }
  ]
}
```

#### VO 建议

```java
public class AssetVO {
    private String symbol;
    private String name;
    private Integer scaleNum;
    private Integer status;
}
```

#### 业务说明

- `status = 1` 表示启用。
- `status = 0` 表示禁用。
- 第一阶段只返回启用币种即可。

---

## 4.2 查询交易对

### GET `/api/markets/symbols`

查询当前支持交易的现货交易对，例如 BTCUSDT、ETHUSDT。

#### 请求参数

无。

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "symbol": "BTCUSDT",
      "baseAsset": "BTC",
      "quoteAsset": "USDT",
      "priceScale": 2,
      "quantityScale": 8,
      "minOrderAmount": "10",
      "status": 1
    }
  ]
}
```

#### VO 建议

```java
public class SymbolPairVO {
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private Integer priceScale;
    private Integer quantityScale;
    private BigDecimal minOrderAmount;
    private Integer status;
}
```

#### 业务说明

- `baseAsset` 是基础币，例如 BTC。
- `quoteAsset` 是计价币，例如 USDT。
- `BTCUSDT` 表示用 USDT 买卖 BTC。
- 第一阶段只需要支持 `BTCUSDT` 和 `ETHUSDT`。

---

## 4.3 管理端新增币种，后续可做

### POST `/api/admin/assets`

第一阶段可以不做。后续做后台管理时再实现。

#### 请求示例

```json
{
  "symbol": "SOL",
  "name": "Solana",
  "scaleNum": 8
}
```

---

## 4.4 管理端新增交易对，后续可做

### POST `/api/admin/symbols`

第一阶段可以不做。后续做后台管理时再实现。

#### 请求示例

```json
{
  "symbol": "SOLUSDT",
  "baseAsset": "SOL",
  "quoteAsset": "USDT",
  "priceScale": 4,
  "quantityScale": 4,
  "minOrderAmount": "5"
}
```

---

# 5. 账户模块 Account API

账户模块是项目第一阶段最重要的模块。先不做 JWT 登录，只做模拟账户创建和查询。

推荐包结构：

```text
com.fffg.cex.account
├── controller
├── service
├── mapper
├── entity
├── dto
└── vo
```

---

## 5.1 创建账户

### POST `/api/accounts`

创建一个模拟交易所账户。

#### 请求体

```json
{
  "username": "alice"
}
```

#### DTO 建议

```java
public class CreateAccountRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度必须在3到32位之间")
    private String username;
}
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accountId": 1,
    "username": "alice",
    "status": 1,
    "createdAt": "2026-05-05T14:00:00"
  }
}
```

#### VO 建议

```java
public class AccountVO {
    private Long accountId;
    private String username;
    private Integer status;
    private LocalDateTime createdAt;
}
```

#### 业务规则

- 用户名不能为空。
- 用户名不能重复。
- 创建账户后，账户状态默认为启用。
- 可以选择在创建账户时初始化 USDT、BTC、ETH 三个余额账户，余额都为 0。

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40006 | 用户名不能为空 | 参数校验失败 |
| 40007 | 用户名已存在 | 重复创建 |

---

## 5.2 查询账户详情

### GET `/api/accounts/{accountId}`

根据账户 ID 查询账户信息。

#### Path 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accountId": 1,
    "username": "alice",
    "status": 1,
    "createdAt": "2026-05-05T14:00:00"
  }
}
```

#### 业务规则

- 如果账户不存在，返回业务异常。

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40001 | 账户不存在 | accountId 不存在 |

---

## 5.3 查询账户余额

### GET `/api/accounts/{accountId}/balances`

查询指定账户下所有资产余额。

#### Path 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "assetSymbol": "USDT",
      "availableBalance": "100000.000000000000000000",
      "frozenBalance": "0.000000000000000000"
    },
    {
      "assetSymbol": "BTC",
      "availableBalance": "1.000000000000000000",
      "frozenBalance": "0.000000000000000000"
    }
  ]
}
```

#### VO 建议

```java
public class AccountBalanceVO {
    private String assetSymbol;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
}
```

#### 字段说明

| 字段 | 说明 |
|---|---|
| availableBalance | 可用余额，可以下单或提现 |
| frozenBalance | 冻结余额，已经被订单或提现占用 |

#### 业务规则

- 查询前先校验账户是否存在。
- 第一阶段可以只返回已经存在的余额记录。
- 更推荐创建账户时初始化常见币种余额，这样前端展示更直观。

---

## 5.4 模拟充值

### POST `/api/accounts/{accountId}/balances/deposit`

给账户模拟充值。第一阶段不接入真实链上充值，用这个接口给测试账户增加余额。

#### Path 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |

#### 请求体

```json
{
  "assetSymbol": "USDT",
  "amount": "100000"
}
```

#### DTO 建议

```java
public class DepositRequest {
    @NotBlank(message = "币种不能为空")
    private String assetSymbol;

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.00000001", message = "充值金额必须大于0")
    private BigDecimal amount;
}
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

#### 业务规则

- 账户必须存在。
- 币种必须存在且启用。
- 充值金额必须大于 0。
- 如果账户没有该币种余额记录，则先创建余额记录。
- 增加 `account_balance.available_balance`。
- 插入一条 `asset_ledger` 流水。
- 余额更新和流水插入必须在同一个事务里完成。

#### 余额变化示例

充值前：

```text
availableBalance = 0
frozenBalance = 0
```

充值 100000 USDT 后：

```text
availableBalance = 100000
frozenBalance = 0
```

流水记录：

```text
businessType = MOCK_DEPOSIT
changeAvailable = 100000
changeFrozen = 0
beforeAvailable = 0
afterAvailable = 100000
beforeFrozen = 0
afterFrozen = 0
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40001 | 账户不存在 | accountId 不存在 |
| 40002 | 币种不存在 | assetSymbol 不存在 |
| 40006 | 充值金额必须大于0 | 参数校验失败 |

---

## 5.5 查询资产流水

### GET `/api/accounts/{accountId}/ledgers`

查询账户资产流水，用于审计和排查资产变化。

#### Path 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| assetSymbol | String | 否 | 币种，例如 USDT |
| businessType | String | 否 | 流水类型 |
| pageNum | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页数量，默认 20 |

#### 请求示例

```text
GET /api/accounts/1/ledgers?assetSymbol=USDT&pageNum=1&pageSize=20
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "assetSymbol": "USDT",
        "businessType": "MOCK_DEPOSIT",
        "businessId": "DEP202605051400000001",
        "changeAvailable": "100000",
        "changeFrozen": "0",
        "beforeAvailable": "0",
        "afterAvailable": "100000",
        "beforeFrozen": "0",
        "afterFrozen": "0",
        "createdAt": "2026-05-05T14:00:00"
      }
    ],
    "pageNum": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

#### VO 建议

```java
public class AssetLedgerVO {
    private Long id;
    private String assetSymbol;
    private String businessType;
    private String businessId;
    private BigDecimal changeAvailable;
    private BigDecimal changeFrozen;
    private BigDecimal beforeAvailable;
    private BigDecimal afterAvailable;
    private BigDecimal beforeFrozen;
    private BigDecimal afterFrozen;
    private LocalDateTime createdAt;
}
```

#### 业务规则

- 资金流水只插入，不更新，不删除。
- 后续任何资产变化都必须写流水。
- 余额表是当前快照，流水表是资产历史。

---

# 6. 订单模块 Order API

订单模块先做限价单。第一版可以先完成订单创建和冻结资产，撮合逻辑放到下一阶段。

推荐包结构：

```text
com.fffg.cex.order
├── controller
├── service
├── mapper
├── entity
├── dto
└── vo
```

---

## 6.1 创建订单

### POST `/api/orders`

提交一个现货限价单。

#### 请求体

买单示例：

```json
{
  "accountId": 1,
  "symbol": "BTCUSDT",
  "side": "BUY",
  "orderType": "LIMIT",
  "price": "60000",
  "quantity": "0.1"
}
```

卖单示例：

```json
{
  "accountId": 2,
  "symbol": "BTCUSDT",
  "side": "SELL",
  "orderType": "LIMIT",
  "price": "61000",
  "quantity": "0.1"
}
```

#### DTO 建议

```java
public class CreateOrderRequest {
    @NotNull(message = "账户ID不能为空")
    private Long accountId;

    @NotBlank(message = "交易对不能为空")
    private String symbol;

    @NotBlank(message = "订单方向不能为空")
    @Pattern(regexp = "BUY|SELL", message = "订单方向只能是 BUY 或 SELL")
    private String side;

    @NotBlank(message = "订单类型不能为空")
    @Pattern(regexp = "LIMIT", message = "第一阶段只支持 LIMIT 限价单")
    private String orderType;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00000001", message = "价格必须大于0")
    private BigDecimal price;

    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.00000001", message = "数量必须大于0")
    private BigDecimal quantity;
}
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 1001,
    "orderNo": "ORD202605051430000001",
    "accountId": 1,
    "symbol": "BTCUSDT",
    "side": "BUY",
    "orderType": "LIMIT",
    "price": "60000",
    "quantity": "0.1",
    "filledQuantity": "0",
    "status": "NEW",
    "createdAt": "2026-05-05T14:30:00"
  }
}
```

#### VO 建议

```java
public class OrderVO {
    private Long orderId;
    private String orderNo;
    private Long accountId;
    private String symbol;
    private String side;
    private String orderType;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private String status;
    private LocalDateTime createdAt;
}
```

#### 业务规则

- 账户必须存在。
- 交易对必须存在且启用。
- 第一阶段只支持 `LIMIT`。
- 买单冻结计价币，例如 BTCUSDT 买单冻结 USDT。
- 卖单冻结基础币，例如 BTCUSDT 卖单冻结 BTC。
- 买单冻结金额：`price * quantity`。
- 卖单冻结数量：`quantity`。
- 冻结资产和创建订单必须放在同一个事务。
- 如果余额不足，订单不能创建。

#### 买单冻结示例

用户下单：

```text
BUY BTCUSDT price=60000 quantity=0.1
```

冻结金额：

```text
60000 * 0.1 = 6000 USDT
```

余额变化：

```text
USDT availableBalance -6000
USDT frozenBalance +6000
```

流水：

```text
businessType = ORDER_FREEZE
changeAvailable = -6000
changeFrozen = +6000
```

#### 卖单冻结示例

用户下单：

```text
SELL BTCUSDT price=61000 quantity=0.1
```

冻结资产：

```text
0.1 BTC
```

余额变化：

```text
BTC availableBalance -0.1
BTC frozenBalance +0.1
```

流水：

```text
businessType = ORDER_FREEZE
changeAvailable = -0.1
changeFrozen = +0.1
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40001 | 账户不存在 | accountId 不存在 |
| 40003 | 交易对不存在 | symbol 不存在 |
| 40004 | 余额不足 | 可用余额不足以冻结 |
| 40006 | 参数错误 | 参数校验失败 |

---

## 6.2 查询订单详情

### GET `/api/orders/{orderId}`

根据订单 ID 查询订单详情。

#### Path 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| orderId | Long | 是 | 订单 ID |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 1001,
    "orderNo": "ORD202605051430000001",
    "accountId": 1,
    "symbol": "BTCUSDT",
    "side": "BUY",
    "orderType": "LIMIT",
    "price": "60000",
    "quantity": "0.1",
    "filledQuantity": "0",
    "status": "NEW",
    "createdAt": "2026-05-05T14:30:00"
  }
}
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40008 | 订单不存在 | orderId 不存在 |

---

## 6.3 查询账户订单列表

### GET `/api/accounts/{accountId}/orders`

查询指定账户的订单列表。

#### Path 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 否 | 交易对 |
| side | String | 否 | BUY 或 SELL |
| status | String | 否 | 订单状态 |
| pageNum | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页数量，默认 20 |

#### 请求示例

```text
GET /api/accounts/1/orders?symbol=BTCUSDT&status=NEW&pageNum=1&pageSize=20
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "orderId": 1001,
        "orderNo": "ORD202605051430000001",
        "symbol": "BTCUSDT",
        "side": "BUY",
        "orderType": "LIMIT",
        "price": "60000",
        "quantity": "0.1",
        "filledQuantity": "0",
        "status": "NEW",
        "createdAt": "2026-05-05T14:30:00"
      }
    ],
    "pageNum": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

---

## 6.4 撤销订单

### POST `/api/orders/{orderId}/cancel`

撤销未完全成交的订单，并释放剩余冻结资产。

#### Path 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| orderId | Long | 是 | 订单 ID |

#### 请求体

可以为空。

```json
{}
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 1001,
    "status": "CANCELED"
  }
}
```

#### 业务规则

- 订单必须存在。
- 只有 `NEW` 或 `PARTIALLY_FILLED` 状态的订单可以撤销。
- 已完全成交 `FILLED` 的订单不能撤销。
- 已撤销 `CANCELED` 的订单不能重复撤销。
- 撤单时需要释放剩余冻结资产。
- 解冻资产、更新订单状态、插入流水必须在同一个事务。

#### 买单撤销解冻

买单剩余未成交数量：

```text
remainingQuantity = quantity - filledQuantity
```

解冻 USDT：

```text
price * remainingQuantity
```

资产变化：

```text
availableBalance + 解冻金额
frozenBalance - 解冻金额
```

流水：

```text
businessType = ORDER_UNFREEZE
```

#### 卖单撤销解冻

卖单剩余未成交数量：

```text
remainingQuantity = quantity - filledQuantity
```

解冻 BTC：

```text
remainingQuantity
```

资产变化：

```text
availableBalance + remainingQuantity
frozenBalance - remainingQuantity
```

流水：

```text
businessType = ORDER_UNFREEZE
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40008 | 订单不存在 | orderId 不存在 |
| 40005 | 订单状态非法 | 已成交或已撤销订单不能撤销 |

---

# 7. 撮合与成交模块 Matching / Trade API

撮合模块是项目核心，但建议在账户和订单冻结逻辑完成后再做。

推荐包结构：

```text
com.fffg.cex.matching
├── MatchEngine.java
├── OrderBook.java
├── MatchResult.java
└── OrderBookManager.java
```

成交查询可以放在：

```text
com.fffg.cex.trade
├── controller
├── service
├── mapper
├── entity
└── vo
```

---

## 7.1 查询最近成交记录

### GET `/api/trades`

查询某个交易对最近成交记录。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 是 | 交易对，例如 BTCUSDT |
| limit | Integer | 否 | 返回数量，默认 50，最大 200 |

#### 请求示例

```text
GET /api/trades?symbol=BTCUSDT&limit=50
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "tradeId": 5001,
      "tradeNo": "TRD202605051500000001",
      "symbol": "BTCUSDT",
      "price": "59900",
      "quantity": "0.1",
      "amount": "5990",
      "buyOrderId": 1001,
      "sellOrderId": 1002,
      "createdAt": "2026-05-05T15:00:00"
    }
  ]
}
```

#### VO 建议

```java
public class TradeVO {
    private Long tradeId;
    private String tradeNo;
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal amount;
    private Long buyOrderId;
    private Long sellOrderId;
    private LocalDateTime createdAt;
}
```

---

## 7.2 撮合逻辑，内部服务，不一定暴露接口

撮合引擎通常不建议直接开放 HTTP 接口，应该由下单服务内部调用。

建议流程：

```text
POST /api/orders
    ↓
校验账户、交易对、余额
    ↓
冻结资产
    ↓
创建订单
    ↓
调用 MatchEngine.match(order)
    ↓
生成成交记录
    ↓
成交结算
    ↓
更新订单状态
    ↓
返回订单结果
```

### 撮合规则

买盘：

```text
价格高者优先
同价格时间早者优先
```

卖盘：

```text
价格低者优先
同价格时间早者优先
```

成交条件：

```text
买价 >= 卖价
```

成交价第一版建议：

```text
取先挂单价格
```

### 例子

买单：

```text
BUY 1 BTC @ 60000
```

卖单：

```text
SELL 0.3 BTC @ 59900
```

因为：

```text
60000 >= 59900
```

所以成交：

```text
price = 59900
quantity = 0.3
amount = 17970
```

---

## 7.3 成交结算规则

### 买方资产变化

买方冻结 USDT，成交后获得 BTC。

```text
USDT frozenBalance -= tradeAmount
BTC availableBalance += tradeQuantity
```

如果买单价格高于实际成交价，需要退还差额。

例如：

```text
买单价格 60000
成交价格 59900
成交数量 0.1
```

实际成交金额：

```text
59900 * 0.1 = 5990
```

原冻结金额：

```text
60000 * 0.1 = 6000
```

差额：

```text
10 USDT
```

需要退回：

```text
USDT availableBalance += 10
USDT frozenBalance -= 6000
BTC availableBalance += 0.1
```

第一版为了简单，也可以先不处理价差，成交价固定取买单价。但面试展示时，建议处理价差。

### 卖方资产变化

卖方冻结 BTC，成交后获得 USDT。

```text
BTC frozenBalance -= tradeQuantity
USDT availableBalance += tradeAmount
```

### 流水类型

| 场景 | businessType |
|---|---|
| 买方获得 BTC | TRADE_BUY |
| 买方扣除 USDT 冻结 | TRADE_BUY_PAY |
| 买方退回价差 | TRADE_REFUND |
| 卖方扣除 BTC 冻结 | TRADE_SELL |
| 卖方获得 USDT | TRADE_SELL_RECEIVE |
| 手续费 | FEE |

第一版可以简化为：

```text
TRADE_BUY
TRADE_SELL
```

---

# 8. 行情模块 Market Data API

行情模块用于展示订单簿、最新成交、ticker、K 线。第一阶段可以先不做，V3/V4 再实现。

---

## 8.1 查询订单簿

### GET `/api/market/depth`

查询指定交易对的订单簿深度。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 是 | 交易对，例如 BTCUSDT |
| limit | Integer | 否 | 档位数量，默认 20 |

#### 请求示例

```text
GET /api/market/depth?symbol=BTCUSDT&limit=20
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "symbol": "BTCUSDT",
    "bids": [
      ["60000", "0.5"],
      ["59900", "1.2"]
    ],
    "asks": [
      ["60100", "0.3"],
      ["60200", "0.8"]
    ],
    "timestamp": 1777950000000
  }
}
```

#### 字段说明

| 字段 | 说明 |
|---|---|
| bids | 买盘，价格从高到低 |
| asks | 卖盘，价格从低到高 |

#### 实现建议

第一版可以直接从 `trade_order` 表中按价格聚合：

```sql
SELECT price, SUM(quantity - filled_quantity)
FROM trade_order
WHERE symbol = ? AND side = 'BUY' AND status IN ('NEW', 'PARTIALLY_FILLED')
GROUP BY price
ORDER BY price DESC
LIMIT ?
```

后续再改成内存 OrderBook 或 Redis。

---

## 8.2 查询最新成交

### GET `/api/market/trades`

这个接口可以和 `/api/trades` 复用，也可以作为行情模块单独提供。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 是 | 交易对 |
| limit | Integer | 否 | 数量，默认 50 |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "price": "59900",
      "quantity": "0.1",
      "amount": "5990",
      "createdAt": "2026-05-05T15:00:00"
    }
  ]
}
```

---

## 8.3 查询 ticker

### GET `/api/market/ticker`

查询某个交易对 24 小时行情概要。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 是 | 交易对 |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "symbol": "BTCUSDT",
    "lastPrice": "59900",
    "openPrice": "58000",
    "highPrice": "61000",
    "lowPrice": "57000",
    "volume": "12.5",
    "amount": "742000",
    "priceChange": "1900",
    "priceChangePercent": "3.27"
  }
}
```

---

## 8.4 查询 K 线

### GET `/api/market/klines`

查询 K 线数据。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 是 | 交易对 |
| interval | String | 是 | 周期，例如 `1m`, `5m`, `15m`, `1h`, `1d` |
| limit | Integer | 否 | 数量，默认 100 |

#### 请求示例

```text
GET /api/market/klines?symbol=BTCUSDT&interval=1m&limit=100
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "openTime": "2026-05-05T15:00:00",
      "openPrice": "59000",
      "highPrice": "60000",
      "lowPrice": "58900",
      "closePrice": "59900",
      "volume": "1.2",
      "amount": "71200"
    }
  ]
}
```

#### 实现建议

- 第一版可以不做。
- 第二版可以根据 `trade_fill` 成交记录定时聚合。
- 第三版可以在撮合成交时实时更新当前 K 线。

---

# 9. 钱包模块 Wallet API，后续扩展

钱包模块用于模拟 Web3 交易所的充值和提现。第一阶段建议只做 `mockDeposit`，真实钱包流程后续再做。

---

## 9.1 获取充值地址，后续

### GET `/api/wallet/deposit-address`

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |
| assetSymbol | String | 是 | 币种 |
| chain | String | 是 | 链，例如 ETH、BSC、TRON |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accountId": 1,
    "assetSymbol": "USDT",
    "chain": "ETH",
    "address": "0x1234567890abcdef"
  }
}
```

---

## 9.2 查询充值记录，后续

### GET `/api/wallet/deposits`

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |
| assetSymbol | String | 否 | 币种 |
| status | String | 否 | 状态 |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "depositId": 1,
      "assetSymbol": "USDT",
      "chain": "ETH",
      "txHash": "0xabc",
      "amount": "100",
      "confirmations": 12,
      "status": "SUCCESS",
      "createdAt": "2026-05-05T16:00:00"
    }
  ]
}
```

---

## 9.3 提现申请，后续

### POST `/api/wallet/withdraws`

#### 请求体

```json
{
  "accountId": 1,
  "assetSymbol": "USDT",
  "chain": "ETH",
  "toAddress": "0xabcdef123456",
  "amount": "100",
  "fee": "1"
}
```

#### 业务规则

- 账户必须存在。
- 币种必须存在。
- 提现金额必须大于 0。
- 可用余额必须大于等于 `amount + fee`。
- 提现申请先冻结资产。
- 大额提现进入人工审核。

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "withdrawId": 1,
    "status": "REVIEWING"
  }
}
```

---

## 9.4 查询提现记录，后续

### GET `/api/wallet/withdraws`

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |
| status | String | 否 | 状态 |

---

## 9.5 管理端审核提现，后续

### POST `/api/admin/withdraws/{withdrawId}/approve`

审核通过提现。

### POST `/api/admin/withdraws/{withdrawId}/reject`

审核拒绝提现，并释放冻结资产。

---

# 10. WebSocket 行情接口，后续扩展

第一阶段不做 WebSocket，等订单簿和成交数据稳定后再做。

## 10.1 订单簿推送

```text
/ws/market/BTCUSDT/depth
```

推送示例：

```json
{
  "channel": "depth",
  "symbol": "BTCUSDT",
  "bids": [["60000", "0.5"]],
  "asks": [["60100", "0.3"]],
  "timestamp": 1777950000000
}
```

## 10.2 最新成交推送

```text
/ws/market/BTCUSDT/trade
```

推送示例：

```json
{
  "channel": "trade",
  "symbol": "BTCUSDT",
  "price": "59900",
  "quantity": "0.1",
  "amount": "5990",
  "timestamp": 1777950000000
}
```

## 10.3 K 线推送

```text
/ws/market/BTCUSDT/kline_1m
```

---

# 11. 错误码设计

建议先定义这些错误码。

```java
public enum ErrorCode {
    ACCOUNT_NOT_FOUND(40001, "账户不存在"),
    ASSET_NOT_FOUND(40002, "币种不存在"),
    SYMBOL_NOT_FOUND(40003, "交易对不存在"),
    INSUFFICIENT_BALANCE(40004, "余额不足"),
    INVALID_ORDER_STATUS(40005, "订单状态非法"),
    PARAM_ERROR(40006, "参数错误"),
    USERNAME_EXISTS(40007, "用户名已存在"),
    ORDER_NOT_FOUND(40008, "订单不存在"),
    ASSET_BALANCE_NOT_FOUND(40009, "资产余额不存在"),
    DUPLICATE_REQUEST(40010, "重复请求"),
    SYSTEM_ERROR(50000, "系统异常");
}
```

错误码表：

| code | message | 说明 |
|---|---|---|
| 0 | success | 成功 |
| 40001 | 账户不存在 | accountId 无效 |
| 40002 | 币种不存在 | assetSymbol 无效 |
| 40003 | 交易对不存在 | symbol 无效 |
| 40004 | 余额不足 | 下单、提现、冻结时余额不足 |
| 40005 | 订单状态非法 | 不能撤销已成交订单等 |
| 40006 | 参数错误 | DTO 参数校验失败 |
| 40007 | 用户名已存在 | 创建账户重复 |
| 40008 | 订单不存在 | orderId 无效 |
| 40009 | 资产余额不存在 | 账户没有该币种余额记录 |
| 40010 | 重复请求 | 幂等控制失败 |
| 50000 | 系统异常 | 未知异常 |

---

# 12. 数据库表建议

## 12.1 account

```sql
CREATE TABLE account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);
```

## 12.2 asset

```sql
CREATE TABLE asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  symbol VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  scale_num INT NOT NULL DEFAULT 8,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL
);
```

## 12.3 symbol_pair

```sql
CREATE TABLE symbol_pair (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  symbol VARCHAR(32) NOT NULL UNIQUE,
  base_asset VARCHAR(20) NOT NULL,
  quote_asset VARCHAR(20) NOT NULL,
  price_scale INT NOT NULL DEFAULT 2,
  quantity_scale INT NOT NULL DEFAULT 8,
  min_order_amount DECIMAL(36,18) NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL
);
```

## 12.4 account_balance

```sql
CREATE TABLE account_balance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  asset_symbol VARCHAR(20) NOT NULL,
  available_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
  frozen_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_account_asset (account_id, asset_symbol)
);
```

## 12.5 asset_ledger

```sql
CREATE TABLE asset_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  asset_symbol VARCHAR(20) NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  business_id VARCHAR(64) NOT NULL,
  change_available DECIMAL(36,18) NOT NULL DEFAULT 0,
  change_frozen DECIMAL(36,18) NOT NULL DEFAULT 0,
  before_available DECIMAL(36,18) NOT NULL,
  after_available DECIMAL(36,18) NOT NULL,
  before_frozen DECIMAL(36,18) NOT NULL,
  after_frozen DECIMAL(36,18) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_account_asset (account_id, asset_symbol),
  INDEX idx_business (business_type, business_id)
);
```

## 12.6 trade_order

```sql
CREATE TABLE trade_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL UNIQUE,
  account_id BIGINT NOT NULL,
  symbol VARCHAR(32) NOT NULL,
  side VARCHAR(10) NOT NULL,
  order_type VARCHAR(20) NOT NULL,
  price DECIMAL(36,18) NOT NULL,
  quantity DECIMAL(36,18) NOT NULL,
  filled_quantity DECIMAL(36,18) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_account_id (account_id),
  INDEX idx_symbol_side_price (symbol, side, price),
  INDEX idx_status (status)
);
```

## 12.7 trade_fill

```sql
CREATE TABLE trade_fill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trade_no VARCHAR(64) NOT NULL UNIQUE,
  symbol VARCHAR(32) NOT NULL,
  buy_order_id BIGINT NOT NULL,
  sell_order_id BIGINT NOT NULL,
  buy_account_id BIGINT NOT NULL,
  sell_account_id BIGINT NOT NULL,
  price DECIMAL(36,18) NOT NULL,
  quantity DECIMAL(36,18) NOT NULL,
  amount DECIMAL(36,18) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_symbol_created_at (symbol, created_at)
);
```

---

# 13. 优先开发顺序

## 第一批：必须先写

```text
1. ApiResponse
2. ErrorCode
3. BusinessException
4. GlobalExceptionHandler
5. HealthController
6. account 表
7. asset 表
8. symbol_pair 表
9. account_balance 表
10. asset_ledger 表
11. MarketController
12. AccountController
13. 模拟充值
14. 查询余额
15. 查询流水
```

完成后应能跑通：

```text
创建账户 -> 查询余额 -> 模拟充值 USDT -> 再次查询余额 -> 查询流水
```

## 第二批：订单基础

```text
1. trade_order 表
2. CreateOrderRequest
3. OrderController
4. 创建限价单
5. 买单冻结 USDT
6. 卖单冻结 BTC
7. 查询订单
8. 撤单解冻
```

完成后应能跑通：

```text
充值 USDT -> 下买单 -> USDT 从 available 进入 frozen -> 撤单 -> USDT 回到 available
充值 BTC -> 下卖单 -> BTC 从 available 进入 frozen -> 撤单 -> BTC 回到 available
```

## 第三批：撮合成交

```text
1. OrderBook
2. MatchEngine
3. trade_fill 表
4. 撮合成交
5. 成交结算
6. 更新订单状态
7. 生成成交流水
8. 查询最近成交
9. 查询订单簿
```

完成后应能跑通：

```text
用户 A 充值 USDT
用户 B 充值 BTC
用户 A 下买单
用户 B 下卖单
系统撮合成交
用户 A 得到 BTC
用户 B 得到 USDT
双方订单状态更新
生成成交记录和资产流水
```

---

# 14. Swagger 注解建议

Controller 上：

```java
@Tag(name = "账户模块", description = "账户创建、余额查询、模拟充值、资产流水")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
}
```

接口上：

```java
@Operation(summary = "创建账户", description = "创建一个模拟交易所账户")
@PostMapping
public ApiResponse<AccountVO> createAccount(@Valid @RequestBody CreateAccountRequest request) {
    return ApiResponse.success(accountService.createAccount(request));
}
```

DTO 字段上：

```java
@Schema(description = "用户名", example = "alice")
@NotBlank(message = "用户名不能为空")
private String username;
```

常用 Tag 建议：

```text
Health API
Market API
Account API
Order API
Trade API
Market Data API
Wallet API
Admin API
```

---

# 15. README 中的接口摘要

README 不需要写所有细节，只写核心接口索引。

```md
## API Documentation

启动项目后访问 Swagger UI：

- http://localhost:8080/swagger-ui.html
- http://localhost:8080/v3/api-docs

### Core APIs

| Method | Path | Description |
|---|---|---|
| GET | /api/health | 健康检查 |
| GET | /api/markets/assets | 查询币种 |
| GET | /api/markets/symbols | 查询交易对 |
| POST | /api/accounts | 创建账户 |
| GET | /api/accounts/{accountId} | 查询账户 |
| GET | /api/accounts/{accountId}/balances | 查询余额 |
| POST | /api/accounts/{accountId}/balances/deposit | 模拟充值 |
| GET | /api/accounts/{accountId}/ledgers | 查询流水 |
| POST | /api/orders | 创建订单 |
| GET | /api/orders/{orderId} | 查询订单 |
| GET | /api/accounts/{accountId}/orders | 查询账户订单 |
| POST | /api/orders/{orderId}/cancel | 撤销订单 |
| GET | /api/trades | 查询成交记录 |
| GET | /api/market/depth | 查询订单簿 |
```

---

# 16. 面试讲解重点

这个项目面试时不要只讲 CRUD，要重点讲以下内容：

## 16.1 双余额模型

```text
availableBalance：可用余额
frozenBalance：冻结余额
```

下单时先冻结，成交时扣冻结，撤单时解冻。

## 16.2 资金流水

```text
account_balance 是当前余额快照
asset_ledger 是资产变化历史
```

如果用户反馈资产不对，可以通过流水回放和排查。

## 16.3 条件更新防止超扣

冻结资产时建议使用 SQL 条件更新：

```sql
UPDATE account_balance
SET available_balance = available_balance - ?,
    frozen_balance = frozen_balance + ?,
    updated_at = NOW()
WHERE account_id = ?
  AND asset_symbol = ?
  AND available_balance >= ?;
```

然后判断影响行数是否为 1。

## 16.4 撮合规则

```text
买单：价格高优先，时间早优先
卖单：价格低优先，时间早优先
买价 >= 卖价 时成交
```

## 16.5 事务一致性

以下操作必须在同一个事务中完成：

```text
下单：冻结资产 + 创建订单 + 生成流水
撤单：更新订单状态 + 解冻资产 + 生成流水
成交：生成成交记录 + 更新订单 + 资产结算 + 生成流水
```

---

# 17. 当前建议你立刻开始实现的接口

第一阶段只写下面 6 个，别贪多：

```text
GET  /api/health
GET  /api/markets/assets
GET  /api/markets/symbols
POST /api/accounts
GET  /api/accounts/{accountId}/balances
POST /api/accounts/{accountId}/balances/deposit
```

做完后再补：

```text
GET /api/accounts/{accountId}
GET /api/accounts/{accountId}/ledgers
```

这 8 个接口完成后，项目基础就稳了，可以进入订单模块。

