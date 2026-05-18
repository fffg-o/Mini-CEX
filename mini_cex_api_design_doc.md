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
  "data": {},
  "traceId": "a1b2c3d4e5f6g7h8"
}
```

> **说明**：所有响应中 `traceId` 为可选字段，用于请求追踪和问题排查。客户端可在请求头 `X-Trace-Id` 中传入自定义 traceId，未传入时由服务端自动生成。

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

- 用户名不能为空，长度 3~32 位，只能包含字母、数字和下划线。
- 用户名不能重复（唯一约束）。
- 创建账户后，账户状态默认为启用。
- **创建账户时必须初始化 USDT、BTC、ETH 三个余额账户**（余额都为 0），不能省略。这样前端展示更直观，后续查询余额接口始终有数据返回。

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40006 | 用户名不能为空 / 用户名长度必须在3到32位之间 | 参数校验失败 |
| 40006 | 用户名只能包含字母、数字和下划线 | 参数校验失败 |
| 40007 | 用户名已存在 | 重复创建 |

#### 实现注意

创建账户时使用 `@Transactional` 保证账户创建和余额初始化的原子性。

```java
@Transactional(rollbackFor = Exception.class)
public AccountVO createAccount(CreateAccountRequestDTO request) {
    // 1. 校验用户名是否已存在（避免唯一约束冲突）
    // 2. 创建账户
    // 3. 初始化 USDT、BTC、ETH 三个余额记录
}
```

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

    /**
     * 幂等键：用于防止重复充值。
     * 客户端每次充值请求应传入唯一值（如UUID），
     * 服务端根据 business_id 唯一约束判断是否已处理。
     * 如果不传，由服务端自动生成。
     */
    private String businessId;
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
- 币种必须存在且启用（通过 `asset` 表校验 `status = 1`）。
- 充值金额必须大于 0。
- **充值金额的小数位数不能超过币种的 `scale_num`**。例如 USDT 的 `scale_num = 6`，则充值金额最多 6 位小数。
- **幂等性控制**：请求中的 `businessId` 对应 `asset_ledger.business_id` 唯一键。如果 `businessId` 已存在，服务端直接返回成功（幂等），避免重复充值。如果未传入 `businessId`，服务端自动生成。
- 如果账户没有该币种余额记录，则先创建余额记录（`available_balance = 0, frozen_balance = 0`）。
- 增加 `account_balance.available_balance`。
- 插入一条 `asset_ledger` 流水（`businessType = MOCK_DEPOSIT`）。
- 余额更新和流水插入必须在同一个事务里完成。

#### 实现注意

```java
@Transactional(rollbackFor = Exception.class)
public void deposit(Long accountId, DepositRequest request) {
    // 1. 校验账户存在
    // 2. 校验币种存在且启用
    // 3. 幂等性校验：businessId 是否已存在
    // 4. 校验金额小数位数 <= asset.scale_num
    // 5. 查询或创建余额记录
    // 6. 条件更新：UPDATE account_balance SET available += ? WHERE account_id=? AND asset_symbol=?
    // 7. 插入流水
}
```

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
- 买单冻结金额：`price * quantity`（结果需按 `priceScale` 截断小数位，使用 `RoundingMode.DOWN`）。
- 卖单冻结数量：`quantity`（需按 `quantityScale` 截断小数位）。
- **精度校验**：`price` 的小数位数不能超过交易对的 `priceScale`；`quantity` 的小数位数不能超过交易对的 `quantityScale`。
- **最小订单金额校验**：买单的 `price * quantity >= minOrderAmount`。
- 冻结资产和创建订单必须放在同一个事务。
- 使用**条件更新**防止超扣，然后判断影响行数是否为 1：

```sql
UPDATE account_balance
SET available_balance = available_balance - ?,
    frozen_balance = frozen_balance + ?,
    updated_at = NOW()
WHERE account_id = ?
  AND asset_symbol = ?
  AND available_balance >= ?;
```

- 订单创建完成后，应立即触发撮合引擎尝试撮合（见 7.2 节）。

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

### 并发安全策略（重要）

撮合引擎在内存中操作订单簿，但订单状态和资产余额存储在数据库中。撤单请求可能随时到来，因此必须处理并发问题。

**推荐方案：按交易对加锁**

```java
// 每个交易对独立加锁，避免不同交易对互相阻塞
public class OrderBookManager {
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public void executeWithLock(String symbol, Runnable action) {
        ReentrantLock lock = lockMap.computeIfAbsent(symbol, k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
```

**锁覆盖范围**：下单、撤单、撮合三个操作必须使用同一把锁。

### 订单簿重启恢复

内存订单簿在服务重启后会丢失，需要在启动时从数据库加载所有 `status IN ('NEW', 'PARTIALLY_FILLED')` 的订单到内存。

### 建议流程

```text
POST /api/orders
    ↓
获取交易对锁(symbol Lock)
    ↓
校验账户、交易对、余额
    ↓
冻结资产（条件更新，影响行数检查）
    ↓
创建订单（status = NEW）
    ↓
调用 MatchEngine.match(order)
    ↓
┌─ 循环撮合：while (可继续成交) ─────────────────────┐
│  从对手方向订单簿取最优订单                           │
│  如果 buyPrice >= sellPrice：                        │
│    成交价 = maker 价格                               │
│    成交数量 = min(taker剩余, maker剩余)               │
│    生成 TradeFill 记录                               │
│    更新双方订单已成交数量                              │
│    执行成交结算（处理价差退款）                        │
│    生成双方资产流水                                  │
│  否则：break                                         │
└────────────────────────────────────────────────────┘
    ↓
释放交易对锁
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

> **⚠️ 必须处理价差**：成交价必须取先挂单价格（maker price），taker 多冻结的差额必须退还。不能"不处理价差"，否则会导致系统资产不平。

### 买方完整结算逻辑

```text
// 1. 扣除冻结（按实际成交金额）
USDT frozenBalance -= tradeAmount

// 2. 获得 BTC
BTC availableBalance += tradeQuantity

// 3. 如果有价差（冻结金额 > 实际成交金额），退还差额
if (frozenAmount > tradeAmount) {
    USDT availableBalance += (frozenAmount - tradeAmount)
}
```

### 卖方完整结算逻辑

卖方冻结 BTC，成交后获得 USDT。

```text
// 1. 扣除冻结
BTC frozenBalance -= tradeQuantity

// 2. 获得 USDT
USDT availableBalance += tradeAmount
```

### 流水类型

| 场景 | businessType | 说明 |
|---|---|---|
| 买方获得 BTC | TRADE_BUY | 增加 BTC available |
| 买方扣除 USDT 冻结 | TRADE_BUY_PAY | 减少 USDT frozen |
| 买方退回价差 | TRADE_REFUND | 增加 USDT available（如有价差） |
| 卖方扣除 BTC 冻结 | TRADE_SELL | 减少 BTC frozen |
| 卖方获得 USDT | TRADE_SELL_RECEIVE | 增加 USDT available |
| 手续费 | FEE | 扣除手续费 |

> **建议**：不要简化成 `TRADE_BUY` / `TRADE_SELL` 两种，细分的流水类型更利于审计和排查。

### trade_fill 表增加手续费字段

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
  buy_fee DECIMAL(36,18) NOT NULL DEFAULT 0,     -- 买方手续费
  sell_fee DECIMAL(36,18) NOT NULL DEFAULT 0,    -- 卖方手续费
  created_at DATETIME NOT NULL,
  INDEX idx_symbol_created_at (symbol, created_at)
);
```

### 手续费规则建议

```text
maker 费率：0.1% （挂单方）
taker 费率：0.1% （吃单方）

买方手续费 = tradeAmount * takerRate  （用 USDT 支付）
卖方手续费 = tradeQuantity * makerRate （用 BTC 支付）
```

---

# 8. 行情模块 Market Data API

行情模块用于展示订单簿、最新成交、ticker、K 线等市场数据。这些数据对外部用户是只读查询接口，由撮合引擎成交后驱动更新。

推荐包结构：

```text
com.fffg.cex.marketdata
├── controller
├── service
├── mapper
├── entity
└── vo
```

---

## 8.1 查询订单簿

### GET `/api/market/depth`

查询指定交易对的订单簿深度，返回当前所有活跃买单和卖单的聚合数量。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 是 | 交易对，例如 BTCUSDT |
| limit | Integer | 否 | 档位数量，默认 20，最大 100 |

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

#### VO 建议

```java
@Data
public class MarketDepthVO {
    private String symbol;
    private List<String[]> bids;    // [price, quantity]
    private List<String[]> asks;    // [price, quantity]
    private Long timestamp;
}
```

> **说明**：bids 和 asks 使用 `List<String[]>` 是因为 Binance 等交易所的 REST API 也使用此格式，每项为 `[price, quantity]` 字符串数组，前端解析更方便。

#### 字段说明

| 字段 | 说明 |
|---|---|
| symbol | 交易对 |
| bids | 买盘，价格从高到低排序 |
| asks | 卖盘，价格从低到高排序 |
| timestamp | 快照生成时间戳（毫秒） |

#### 业务规则

- `bids` 按价格降序排列（最高买价在前）。
- `asks` 按价格升序排列（最低卖价在前）。
- 只统计 `status IN ('NEW', 'PARTIALLY_FILLED')` 的活跃订单。
- 同价格的订单数量合并后返回。
- `quantity` 为 `quantity - filled_quantity`（剩余未成交数量）。

#### 实现建议

**第一版（直接从数据库聚合）：**

```sql
-- 买单：按价格降序
SELECT price, SUM(quantity - filled_quantity) AS total_remaining
FROM trade_order
WHERE symbol = ? AND side = 'BUY' AND status IN ('NEW', 'PARTIALLY_FILLED')
GROUP BY price
ORDER BY price DESC
LIMIT ?;

-- 卖单：按价格升序
SELECT price, SUM(quantity - filled_quantity) AS total_remaining
FROM trade_order
WHERE symbol = ? AND side = 'SELL' AND status IN ('NEW', 'PARTIALLY_FILLED')
GROUP BY price
ORDER BY price ASC
LIMIT ?;
```

该方法实现最简单，但高并发下数据库压力大。适合第一版快速验证。

**第二版（内存 OrderBook 快照）：**

在 [`OrderBook.java`](src/main/java/com/fffg/cex/matching/OrderBook.java) 中维护内存订单簿，每次撮合或撤单后更新，`/api/market/depth` 直接从内存读取：

```java
// OrderBook 中维护
private final TreeMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
private final TreeMap<BigDecimal, BigDecimal> asks = new TreeMap<>(Comparator.naturalOrder());

public MarketDepthVO getDepth(String symbol, int limit) {
    // 从 TreeMap 中取前 limit 档
    List<String[]> bidEntries = bids.entrySet().stream()
        .limit(limit)
        .map(e -> new String[]{e.getKey().toPlainString(), e.getValue().toPlainString()})
        .collect(Collectors.toList());
    // asks 同理
    return new MarketDepthVO(symbol, bidEntries, askEntries, System.currentTimeMillis());
}
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40003 | 交易对不存在 | symbol 不存在或未启用 |

---

## 8.2 查询最新成交

### GET `/api/market/trades`

查询某个交易对最近成交记录。该接口与 [`GET /api/trades`](mini_cex_api_design_doc.md:1187) 功能一致，但本接口返回格式更精简，适合行情展示。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 是 | 交易对，例如 BTCUSDT |
| limit | Integer | 否 | 返回数量，默认 50，最大 200 |

#### 请求示例

```text
GET /api/market/trades?symbol=BTCUSDT&limit=50
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "tradeId": 5001,
      "price": "59900",
      "quantity": "0.1",
      "amount": "5990",
      "createdAt": "2026-05-05T15:00:00"
    }
  ]
}
```

#### VO 建议

```java
public class MarketTradeVO {
    private Long tradeId;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
```

#### 业务规则

- 按成交时间倒序返回（最新成交在前）。
- `limit` 最大不超过 200，防止一次查询数据量过大。
- 数据来源为 [`trade_fill`](mini_cex_api_design_doc.md:2006) 表。

#### 实现建议

```java
@GetMapping("/api/market/trades")
public ApiResponse<List<MarketTradeVO>> getRecentTrades(
        @RequestParam String symbol,
        @RequestParam(defaultValue = "50") @Max(200) Integer limit) {
    // 从 trade_fill 表查询最近 limit 条记录
    // SELECT * FROM trade_fill WHERE symbol = ? ORDER BY created_at DESC LIMIT ?
    List<MarketTradeVO> trades = tradeMapper.selectRecentBySymbol(symbol, limit);
    return ApiResponse.success(trades);
}
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40003 | 交易对不存在 | symbol 不存在或未启用 |

---

## 8.3 查询 ticker

### GET `/api/market/ticker`

查询某个交易对 24 小时行情概要，包括最新价、开盘价、最高价、最低价、成交量、成交额、涨跌幅等。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 是 | 交易对，例如 BTCUSDT |

#### 请求示例

```text
GET /api/market/ticker?symbol=BTCUSDT
```

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

#### VO 建议

```java
public class TickerVO {
    private String symbol;
    private BigDecimal lastPrice;        // 最新成交价
    private BigDecimal openPrice;        // 24小时前开盘价
    private BigDecimal highPrice;        // 24小时最高价
    private BigDecimal lowPrice;         // 24小时最低价
    private BigDecimal volume;           // 24小时成交量（基础币数量）
    private BigDecimal amount;           // 24小时成交额（计价币数量）
    private BigDecimal priceChange;      // 价格变化 = lastPrice - openPrice
    private BigDecimal priceChangePercent; // 涨跌幅百分比，保留两位小数
}
```

#### 字段计算规则

| 字段 | 计算方式 |
|---|---|
| lastPrice | 最近一条成交记录的 price |
| openPrice | 24 小时前第一条成交记录的 price，若没有数据则取 lastPrice |
| highPrice | 24 小时内成交的最高 price |
| lowPrice | 24 小时内成交的最低 price |
| volume | 24 小时内所有成交的 quantity 之和 |
| amount | 24 小时内所有成交的 amount 之和 |
| priceChange | `lastPrice - openPrice` |
| priceChangePercent | `(priceChange / openPrice) * 100`，保留两位小数 |

#### 实现建议

```sql
-- 24 小时内 ticker 聚合查询
SELECT
    MAX(created_at) AS last_trade_time,
    -- lastPrice：取最新一条成交价
    (SELECT price FROM trade_fill
     WHERE symbol = ? AND created_at >= NOW() - INTERVAL 24 HOUR
     ORDER BY created_at DESC LIMIT 1) AS last_price,
    -- 24h 前的开盘价
    (SELECT price FROM trade_fill
     WHERE symbol = ? AND created_at >= NOW() - INTERVAL 24 HOUR
     ORDER BY created_at ASC LIMIT 1) AS open_price,
    MAX(price) AS high_price,
    MIN(price) AS low_price,
    SUM(quantity) AS volume,
    SUM(amount) AS amount
FROM trade_fill
WHERE symbol = ?
  AND created_at >= NOW() - INTERVAL 24 HOUR;
```

#### 业务规则

- 如果 24 小时内没有成交数据，返回最近一条成交价作为 lastPrice，openPrice 与 lastPrice 相同，priceChange = 0，priceChangePercent = 0。
- 如果系统刚启动且没有任何成交记录，所有价格字段返回 `"0"`。
- 建议加一层缓存，减少数据库查询频率，例如每 10 秒刷新一次。

#### 缓存建议

```java
@Component
public class TickerCache {
    private final ConcurrentHashMap<String, TickerVO> cache = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 10000) // 每 10 秒刷新
    public void refreshTickers() {
        List<String> symbols = symbolPairService.getEnabledSymbols();
        for (String symbol : symbols) {
            TickerVO ticker = tradeService.calculateTicker(symbol);
            cache.put(symbol, ticker);
        }
    }

    public TickerVO getTicker(String symbol) {
        return cache.getOrDefault(symbol, TickerVO.empty(symbol));
    }
}
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40003 | 交易对不存在 | symbol 不存在或未启用 |

---

## 8.4 查询 K 线

### GET `/api/market/klines`

查询 K 线（蜡烛图）数据，用于前端绘制图表。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| symbol | String | 是 | 交易对，例如 BTCUSDT |
| interval | String | 是 | 周期，支持 `1m`, `5m`, `15m`, `30m`, `1h`, `4h`, `1d`, `1w` |
| limit | Integer | 否 | 返回 K 线数量，默认 100，最大 500 |

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

#### VO 建议

```java
public class KlineVO {
    private LocalDateTime openTime;    // K 线开盘时间
    private BigDecimal openPrice;      // 开盘价
    private BigDecimal highPrice;      // 最高价
    private BigDecimal lowPrice;       // 最低价
    private BigDecimal closePrice;     // 收盘价
    private BigDecimal volume;         // 成交量（基础币）
    private BigDecimal amount;         // 成交额（计价币）
}
```

#### Interval 周期对照表

| interval 值 | 说明 | 时间窗口 |
|---|---|---|
| `1m` | 1 分钟 | `FLOOR(分钟) * 1` |
| `5m` | 5 分钟 | `FLOOR(分钟 / 5) * 5` |
| `15m` | 15 分钟 | `FLOOR(分钟 / 15) * 15` |
| `30m` | 30 分钟 | `FLOOR(分钟 / 30) * 30` |
| `1h` | 1 小时 | `FLOOR(小时) * 1` |
| `4h` | 4 小时 | `FLOOR(小时 / 4) * 4` |
| `1d` | 1 天 | 按自然日 |
| `1w` | 1 周 | 按自然周（周一开盘） |

#### 业务规则

- 按 `openTime` 降序返回（最新的 K 线在前）。
- 每个 K 线的时间窗口根据 `interval` 和成交时间计算所属周期。
- 一根 K 线包含该时间窗口内的所有成交记录聚合。
- 如果某个时间窗口内没有成交记录，则**不返回**该 K 线（第一阶段简单处理）。

#### 实现建议

**第一版（SQL 聚合，适用数据量小的阶段）：**

```java
public List<KlineVO> getKlines(String symbol, String interval, int limit) {
    // 1. 计算时间窗口开始点（如 1m = 向下取整到分钟）
    // 2. 按时间窗口分组聚合
    // SQL 实现参考：
    String sql = "SELECT " +
        "FLOOR(UNIX_TIMESTAMP(created_at) / ?) * ? AS open_time, " +  // 时间窗口分组
        "SUBSTRING_INDEX(GROUP_CONCAT(price ORDER BY created_at), ',', 1) AS open_price, " +
        "MAX(price) AS high_price, " +
        "MIN(price) AS low_price, " +
        "SUBSTRING_INDEX(GROUP_CONCAT(price ORDER BY created_at DESC), ',', 1) AS close_price, " +
        "SUM(quantity) AS volume, " +
        "SUM(amount) AS amount " +
        "FROM trade_fill " +
        "WHERE symbol = ? AND created_at >= ? " +
        "GROUP BY open_time " +
        "ORDER BY open_time DESC LIMIT ?";
    // 执行查询并映射为 KlineVO 列表
}
```

> **注意**：`GROUP_CONCAT` 依赖数据库实现，MySQL 支持。如果使用 H2 内存数据库测试，需要改用其他方式。

**第二版（定时任务聚合 + 缓存表，推荐）：**

创建一张 `kline_data` 表，由定时任务定期从 `trade_fill` 聚合：

```sql
CREATE TABLE kline_data (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  symbol VARCHAR(32) NOT NULL,
  interval_type VARCHAR(10) NOT NULL,   -- 1m, 5m, 1h, 1d
  open_time DATETIME NOT NULL,
  open_price DECIMAL(36,18) NOT NULL,
  high_price DECIMAL(36,18) NOT NULL,
  low_price DECIMAL(36,18) NOT NULL,
  close_price DECIMAL(36,18) NOT NULL,
  volume DECIMAL(36,18) NOT NULL,
  amount DECIMAL(36,18) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_symbol_interval_time (symbol, interval_type, open_time)
);
```

定时任务伪代码：

```java
@Component
public class KlineAggregator {

    @Scheduled(fixedRate = 60000) // 每 1 分钟聚合一次
    public void aggregateKlines() {
        // 1. 查询 trade_fill 中上次聚合时间至今的数据
        // 2. 按 interval 分组聚合
        // 3. 使用 INSERT ... ON DUPLICATE KEY UPDATE 写入 kline_data
        // 4. 更新上次聚合时间游标
    }
}
```

**第三版（撮合时实时更新，高性能）：**

在 [`MatchEngine`](src/main/java/com/fffg/cex/matching/MatchEngine.java) 每次成交后，直接更新当前时间窗口的内存 K 线对象，定时持久化到数据库。

```java
// MatchEngine 成交后调用
public void onTrade(TradeFill trade) {
    KlineCache.update(trade.getSymbol(), "1m", trade);
    KlineCache.update(trade.getSymbol(), "5m", trade);
    // ...
}
```

#### K 线聚合逻辑伪代码

```text
function aggregateKlines(trades, interval):
    grouped = groupBy(trades, t -> getWindowStart(t.createdAt, interval))
    for each (windowStart, groupTrades) in grouped:
        sorted = groupTrades.sortBy(createdAt)
        kline.openPrice = sorted.first().price
        kline.highPrice = sorted.max(price)
        kline.lowPrice = sorted.min(price)
        kline.closePrice = sorted.last().price
        kline.volume = sum(sorted.quantity)
        kline.amount = sum(sorted.amount)
        kline.openTime = windowStart
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40003 | 交易对不存在 | symbol 不存在或未启用 |
| 40006 | 不支持的 K 线周期 | interval 不是支持的周期值 |

---


# 9. 钱包模块 Wallet API

钱包模块用于模拟交易所的充值和提现流程。与第 5.4 节的"模拟充值"不同，钱包模块提供更完整的 Web3 风格充提流程，包括充值地址、链上确认、提现审核等。

> **开发阶段说明**：钱包模块属于 V5 扩展功能，建议在 V0-V4 全部完成后实现。第一阶段核心充提直接使用 [`5.4 模拟充值`](mini_cex_api_design_doc.md:538) 即可。

推荐包结构：

```text
com.fffg.cex.wallet
├── controller
├── service
├── mapper
├── entity
├── dto
└── vo
```

---

## 9.1 获取充值地址

### GET `/api/wallet/deposit-address`

为指定账户和币种生成/查询充值地址。模拟场景下，系统为每个账户的每种币在每个链上生成一个固定地址。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |
| assetSymbol | String | 是 | 币种，例如 USDT |
| chain | String | 是 | 链名称，例如 ETH、BSC、TRON |

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

#### VO 建议

```java
public class DepositAddressVO {
    private Long accountId;
    private String assetSymbol;
    private String chain;
    private String address;
}
```

#### 业务规则

- 账户必须存在。
- 币种必须存在且启用。
- 同一账户、同一币种、同一链的充值地址固定不变。
- 第一次查询时自动生成地址，后续查询直接返回已有地址。
- 地址生成规则：模拟场景下可以按 `accountId + assetSymbol + chain` 哈希取前 40 位作为地址。

#### 地址生成示例

```java
public String generateAddress(Long accountId, String assetSymbol, String chain) {
    String raw = accountId + ":" + assetSymbol + ":" + chain;
    return "0x" + DigestUtils.sha256Hex(raw).substring(0, 40);
}
```

#### 实现建议

```java
// 先用内存缓存模拟，后续可以建 deposit_address 表持久化
@Component
public class DepositAddressManager {
    private final Map<String, String> addressCache = new ConcurrentHashMap<>();

    public String getOrCreateAddress(Long accountId, String assetSymbol, String chain) {
        String key = accountId + ":" + assetSymbol + ":" + chain;
        return addressCache.computeIfAbsent(key, k -> generateAddress(accountId, assetSymbol, chain));
    }
}
```

#### 数据库表建议（后续扩展）

```sql
CREATE TABLE deposit_address (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  asset_symbol VARCHAR(20) NOT NULL,
  chain VARCHAR(20) NOT NULL,
  address VARCHAR(128) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_account_asset_chain (account_id, asset_symbol, chain)
);
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40001 | 账户不存在 | accountId 不存在 |
| 40002 | 币种不存在 | assetSymbol 不存在或未启用 |
| 40006 | 链参数不能为空 | chain 未传入或为空 |

---

## 9.2 查询充值记录

### GET `/api/wallet/deposits`

查询指定账户的链上充值记录。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |
| assetSymbol | String | 否 | 币种筛选 |
| status | String | 否 | 状态筛选，例如 `PENDING`, `SUCCESS`, `FAILED` |
| pageNum | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页数量，默认 20 |

#### 请求示例

```text
GET /api/wallet/deposits?accountId=1&assetSymbol=USDT&status=SUCCESS&pageNum=1&pageSize=20
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "depositId": 1,
        "accountId": 1,
        "assetSymbol": "USDT",
        "chain": "ETH",
        "txHash": "0xabc123def456",
        "amount": "100.000000",
        "confirmations": 12,
        "requiredConfirmations": 12,
        "status": "SUCCESS",
        "createdAt": "2026-05-05T16:00:00",
        "confirmedAt": "2026-05-05T16:05:00"
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
public class DepositRecordVO {
    private Long depositId;
    private Long accountId;
    private String assetSymbol;
    private String chain;
    private String txHash;
    private BigDecimal amount;
    private Integer confirmations;          // 当前确认数
    private Integer requiredConfirmations;  // 要求确认数
    private String status;                  // PENDING / SUCCESS / FAILED
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
}
```

#### 充值状态流转

```text
PENDING  --(确认数达到要求)--> SUCCESS
PENDING  --(链上失败)-------> FAILED
```

#### 业务规则

- 充值记录创建时状态为 `PENDING`。
- `confirmations` 初始为 0，模拟定时任务递增确认数。
- 当 `confirmations >= requiredConfirmations` 时，状态变为 `SUCCESS`，同时**增加账户余额**并记录流水。
- 余额增加逻辑复用 [`DepositRequest`](mini_cex_api_design_doc.md:561) 的幂等充值逻辑。
- 充值到账后，`confirmedAt` 记录到账时间。

#### 模拟确认任务

```java
@Component
public class DepositConfirmationSimulator {

    @Scheduled(fixedRate = 5000) // 每 5 秒模拟一次
    public void simulateConfirmations() {
        // 1. 查询所有 status = 'PENDING' 的充值记录
        // 2. 每笔记录的 confirmations += 1
        // 3. 如果 confirmations >= requiredConfirmations：
        //    a. 更新 status = 'SUCCESS'
        //    b. 调用 accountService.deposit() 增加余额
        //    c. 记录 confirmedAt
    }
}
```

#### 数据库表建议

```sql
CREATE TABLE deposit_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  asset_symbol VARCHAR(20) NOT NULL,
  chain VARCHAR(20) NOT NULL,
  tx_hash VARCHAR(128) NOT NULL UNIQUE,
  amount DECIMAL(36,18) NOT NULL,
  confirmations INT NOT NULL DEFAULT 0,
  required_confirmations INT NOT NULL DEFAULT 12,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL,
  confirmed_at DATETIME,
  INDEX idx_account_id (account_id),
  INDEX idx_status (status)
);
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40001 | 账户不存在 | accountId 不存在 |

---

## 9.3 提现申请

### POST `/api/wallet/withdraws`

提交提现申请。系统先冻结资产，然后进入审核流程。

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

#### DTO 建议

```java
public class WithdrawRequestDTO {
    @NotNull(message = "账户ID不能为空")
    private Long accountId;

    @NotBlank(message = "币种不能为空")
    private String assetSymbol;

    @NotBlank(message = "链不能为空")
    private String chain;

    @NotBlank(message = "提现地址不能为空")
    private String toAddress;

    @NotNull(message = "提现金额不能为空")
    @DecimalMin(value = "0.00000001", message = "提现金额必须大于0")
    private BigDecimal amount;

    @NotNull(message = "手续费不能为空")
    @DecimalMin(value = "0", message = "手续费不能为负数")
    private BigDecimal fee;

    /**
     * 幂等键（可选），防止重复提交
     */
    private String businessId;
}
```

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

#### VO 建议

```java
public class WithdrawResultVO {
    private Long withdrawId;
    private String status;  // REVIEWING / APPROVED / REJECTED / COMPLETED / FAILED
}
```

#### 提现状态流转

```text
REVIEWING  --(审核通过)--> APPROVED --(链上处理)--> COMPLETED
REVIEWING  --(审核拒绝)--> REJECTED
APPROVED   --(链上失败)--> FAILED
```

#### 业务规则

- 账户必须存在。
- 币种必须存在且启用。
- 提现金额必须大于 0。
- 手续费可以为 0（但不可为负数）。
- **可用余额必须大于等于 `amount + fee`**。
- 使用条件更新冻结资产，防止超扣：

```sql
UPDATE account_balance
SET available_balance = available_balance - ?,
    frozen_balance = frozen_balance + ?,
    updated_at = NOW()
WHERE account_id = ?
  AND asset_symbol = ?
  AND available_balance >= ?;
```

- 冻结金额 = `amount + fee`。
- 大额提现规则：单笔金额 ≥ 10000 USDT 或等值自动进入人工审核；小额提现可配置为自动审核通过。
- 冻结资产后，记录 [`asset_ledger`](mini_cex_api_design_doc.md:1961) 流水（`businessType = WITHDRAW_FREEZE`）。
- 提现申请使用 `@Transactional` 保证事务一致性。

#### 实现注意

```java
@Transactional(rollbackFor = Exception.class)
public WithdrawResultVO applyWithdraw(WithdrawRequestDTO request) {
    // 1. 校验账户存在
    // 2. 校验币种存在且启用
    // 3. 校验余额充足（可用余额 >= amount + fee）
    // 4. 条件更新冻结资产
    // 5. 创建提现记录（status = REVIEWING 或 AUTO_APPROVED）
    // 6. 生成资产流水（WITHDRAW_FREEZE）
    // 7. 如果小额自动审核，直接进入 APPROVED 并触发后续流程
}
```

#### 提现金额与冻结示例

```text
提现 100 USDT，手续费 1 USDT
冻结金额 = 100 + 1 = 101 USDT

余额变化：
USDT availableBalance -101
USDT frozenBalance  +101

流水：
businessType = WITHDRAW_FREEZE
changeAvailable = -101
changeFrozen = +101
```

#### 数据库表建议

```sql
CREATE TABLE withdraw_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  asset_symbol VARCHAR(20) NOT NULL,
  chain VARCHAR(20) NOT NULL,
  to_address VARCHAR(128) NOT NULL,
  amount DECIMAL(36,18) NOT NULL,
  fee DECIMAL(36,18) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'REVIEWING',
  tx_hash VARCHAR(128),
  business_id VARCHAR(64) UNIQUE,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_account_id (account_id),
  INDEX idx_status (status)
);
```

#### 可能错误

| code | message | 场景 |
|---|---|---|
| 40001 | 账户不存在 | accountId 不存在 |
| 40002 | 币种不存在 | assetSymbol 不存在或未启用 |
| 40004 | 余额不足 | 可用余额小于 amount + fee |
| 40006 | 提现金额或手续费不合法 | 参数校验失败 |
| 40010 | 重复请求 | businessId 已存在 |

---

## 9.4 查询提现记录

### GET `/api/wallet/withdraws`

查询指定账户的提现记录列表。

#### Query 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| accountId | Long | 是 | 账户 ID |
| status | String | 否 | 状态筛选，例如 `REVIEWING`, `APPROVED`, `REJECTED` |
| pageNum | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页数量，默认 20 |

#### 请求示例

```text
GET /api/wallet/withdraws?accountId=1&status=REVIEWING&pageNum=1&pageSize=20
```

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "withdrawId": 1,
        "accountId": 1,
        "assetSymbol": "USDT",
        "chain": "ETH",
        "toAddress": "0xabcdef123456",
        "amount": "100.000000",
        "fee": "1.000000",
        "status": "REVIEWING",
        "txHash": null,
        "createdAt": "2026-05-05T16:00:00"
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
public class WithdrawRecordVO {
    private Long withdrawId;
    private Long accountId;
    private String assetSymbol;
    private String chain;
    private String toAddress;
    private BigDecimal amount;
    private BigDecimal fee;
    private String status;
    private String txHash;
    private LocalDateTime createdAt;
}
```

#### 业务规则

- 按创建时间倒序排列。
- 支持按 `status` 筛选，不传则查询所有状态。

---

## 9.5 管理端审核提现

### POST `/api/admin/withdraws/{withdrawId}/approve`

审核通过提现申请，将提现状态更新为 `APPROVED`，等待后续链上处理。

#### Path 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| withdrawId | Long | 是 | 提现记录 ID |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "withdrawId": 1,
    "status": "APPROVED"
  }
}
```

#### 业务规则

- 提现记录必须存在。
- 只有 `REVIEWING` 状态的提现可以审核通过。
- 审核通过后状态变为 `APPROVED`。
- 状态变更后，触发模拟链上处理任务（可选）。

#### 实现建议

```java
@Transactional(rollbackFor = Exception.class)
public void approveWithdraw(Long withdrawId) {
    WithdrawRecord record = withdrawMapper.selectById(withdrawId);
    if (record == null) {
        throw new BusinessException(ErrorCode.WITHDRAW_NOT_FOUND);
    }
    if (!"REVIEWING".equals(record.getStatus())) {
        throw new BusinessException(ErrorCode.INVALID_WITHDRAW_STATUS);
    }
    // 更新状态为 APPROVED
    withdrawMapper.updateStatus(withdrawId, "REVIEWING", "APPROVED");
    // 可触发链上处理模拟
}
```

---

### POST `/api/admin/withdraws/{withdrawId}/reject`

审核拒绝提现申请，释放之前冻结的资产。

#### Path 参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| withdrawId | Long | 是 | 提现记录 ID |

#### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "withdrawId": 1,
    "status": "REJECTED"
  }
}
```

#### 业务规则

- 提现记录必须存在。
- 只有 `REVIEWING` 状态的提现可以拒绝。
- 拒绝时需**解冻之前冻结的资产**（`amount + fee`）。
- 解冻资产、更新状态、插入流水必须在同一个事务中。

#### 解冻逻辑

```sql
UPDATE account_balance
SET available_balance = available_balance + ?,
    frozen_balance = frozen_balance - ?,
    updated_at = NOW()
WHERE account_id = ?
  AND asset_symbol = ?;
```

流水：

```text
businessType = WITHDRAW_UNFREEZE
changeAvailable = +(amount + fee)
changeFrozen = -(amount + fee)
```

#### 实现建议

```java
@Transactional(rollbackFor = Exception.class)
public void rejectWithdraw(Long withdrawId) {
    // 1. 查询提现记录
    // 2. 校验状态为 REVIEWING
    // 3. 更新状态为 REJECTED
    // 4. 解冻资产（可用余额 += amount + fee, 冻结余额 -= amount + fee）
    // 5. 插入 asset_ledger 流水（WITHDRAW_UNFREEZE）
}
```

#### 可能错误（审核相关）

| code | message | 场景 |
|---|---|---|
| 40016 | 提现记录不存在 | withdrawId 不存在 |
| 40017 | 提现状态非法 | 非 REVIEWING 状态的提现不能审核 |

---

## 9.6 模拟链上提现完成（内部调度，非 API）

提现审核通过后，系统内部模拟链上处理完成，将状态从 `APPROVED` 转为 `COMPLETED`，并从冻结余额中扣除资产。

```java
@Component
public class WithdrawCompletionSimulator {

    @Scheduled(fixedRate = 30000) // 每 30 秒模拟一次
    public void simulateCompletion() {
        // 1. 查询所有 status = 'APPROVED' 的提现记录
        // 2. 模拟链上处理：
        //    a. 扣除冻结余额（frozen_balance -= amount + fee）
        //    b. 插入流水（WITHDRAW_COMPLETE）
        //    c. 更新 status = 'COMPLETED'
        //    d. 记录 txHash
    }
}
```

```text
链上完成时资产变化：
USDT frozenBalance -(amount + fee)

流水：
businessType = WITHDRAW_COMPLETE
changeFrozen = -(amount + fee)
```

---

## 9.7 钱包模块流水类型汇总

| businessType | 说明 | 可用余额变化 | 冻结余额变化 |
|---|---|---|---|
| WITHDRAW_FREEZE | 提现冻结 | - (amount + fee) | + (amount + fee) |
| WITHDRAW_UNFREEZE | 提现拒绝解冻 | + (amount + fee) | - (amount + fee) |
| WITHDRAW_COMPLETE | 提现完成扣减 | 0 | - (amount + fee) |

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
    WITHDRAW_NOT_FOUND(40016, "提现记录不存在"),
    INVALID_WITHDRAW_STATUS(40017, "提现状态非法"),
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
| 40005 | 订单状态非法 | 不能撤销已成交/已撤销订单 |
| 40006 | 参数错误 | DTO 参数校验失败 |
| 40007 | 用户名已存在 | 创建账户重复 |
| 40008 | 订单不存在 | orderId 无效 |
| 40009 | 资产余额不存在 | 账户没有该币种余额记录 |
| 40010 | 重复请求 | 幂等控制失败 |
| 40011 | 订单已完全成交 | 完全成交的订单不能撤销 |
| 40012 | 订单已撤销 | 已撤销的订单不能重复撤销 |
| 40013 | 价格小数位数超出限制 | 价格精度超过交易对的 priceScale |
| 40014 | 数量小数位数超出限制 | 数量精度超过交易对的 quantityScale |
| 40015 | 订单金额小于最小交易金额 | price * quantity < minOrderAmount |
| 40016 | 提现记录不存在 | withdrawId 无效 |
| 40017 | 提现状态非法 | 非 REVIEWING 状态的提现不能审核 |
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
  UNIQUE KEY uk_business_id (business_id),    -- 业务幂等性控制
  INDEX idx_business_type (business_type)
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
  INDEX idx_account_symbol_status (account_id, symbol, status),  -- 查询账户订单列表
  INDEX idx_symbol_side_price (symbol, side, price),             -- 撮合查询对手盘
  INDEX idx_status (status)                                       -- 启动时加载有效订单
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
  buy_fee DECIMAL(36,18) NOT NULL DEFAULT 0,    -- 买方手续费（USDT）
  sell_fee DECIMAL(36,18) NOT NULL DEFAULT 0,   -- 卖方手续费（BTC）
  created_at DATETIME NOT NULL,
  INDEX idx_symbol_created_at (symbol, created_at),
  INDEX idx_buy_order_id (buy_order_id),
  INDEX idx_sell_order_id (sell_order_id)
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

启动项目后访问 Swagger UI（注意 `/api` 上下文路径）：

- http://localhost:8080/api/swagger-ui/index.html
- http://localhost:8080/api/v3/api-docs

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
| GET | /api/market/trades | 查询最新成交（行情） |
| GET | /api/market/ticker | 查询 24h ticker |
| GET | /api/market/klines | 查询 K 线 |
| GET | /api/wallet/deposit-address | 获取充值地址 |
| GET | /api/wallet/deposits | 查询充值记录 |
| POST | /api/wallet/withdraws | 提现申请 |
| GET | /api/wallet/withdraws | 查询提现记录 |
| POST | /api/admin/withdraws/{withdrawId}/approve | 审核通过提现 |
| POST | /api/admin/withdraws/{withdrawId}/reject | 审核拒绝提现 |
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

