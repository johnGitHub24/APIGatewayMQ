# API 規格書 — APIGatewayMQ

> **本文件為 API 契約的完整參考。**  
> 與 [`APIGatewayMQ 規格書.md`](APIGatewayMQ%20規格書.md) 第 4 章互為補充；若有衝突以主規格書為準。  
> Engine 端點的風控規則、狀態機與 Trading System MVP 規格書一致。

---

## 0. 路由總覽

| Method | 路徑 | 走哪條路 | 回應 | 說明 |
|--------|------|----------|------|------|
| POST | `/api/v1/orders` | Gateway → Kafka | **202** | 正式非同步下單 |
| GET | `/api/v1/orders?clientOrderId=` | Gateway → Engine | 200 | 輪詢訂單狀態 |
| GET | `/api/v1/orders/{id}` | Gateway → Engine | 200 | 依 ID 查單筆 |
| GET | `/api/v1/orders` | Gateway → Engine | 200 | 訂單列表 |
| GET | `/api/v1/positions` | Gateway → Engine | 200 | 查持倉 |
| GET | `/api/v1/pnl` | Gateway → Engine | 200 | 查損益 |
| GET | `/api/v1/trades` | Gateway → Engine | 200 | 成交列表 |
| POST | `/api/v1/orders/{id}/fill` | Gateway → Engine | 200 | 手動補成交 |
| PATCH | `/api/v1/orders/{id}/cancel` | Gateway → Engine | 200 | 取消訂單 |
| GET | `/api/v1/orders/{id}/events` | Gateway → Engine | 200 | 訂單審計事件 |
| POST | `/api/v1/orders` | Engine 直連 (:8081) | 201 | 同步下單（測試用） |

**Base URL**

| 環境 | Gateway | Engine（直連，除錯用） |
|------|---------|------------------------|
| Docker 全棧 | `http://localhost:8080` | `http://localhost:8081` |
| 本機開發 | `http://localhost:8080` | `http://localhost:8081` |

**OpenAPI / Swagger**

| 服務 | URL |
|------|-----|
| Gateway | http://localhost:8080/swagger-ui.html |
| Engine | http://localhost:8081/swagger-ui/index.html |

---

## 1. Gateway 非同步下單

### POST `/api/v1/orders`

將下單指令排入 Kafka，立即回 202，Client 以 `pollUrl` 輪詢結果。

#### Request Headers

| Header | 必填 | 說明 |
|--------|------|------|
| `Content-Type` | 是 | `application/json` |
| `Idempotency-Key` | 建議 | 冪等鍵；對應 `clientOrderId`；重送不會重複建單 |

> 若未提供 `Idempotency-Key`，可使用 body 的 `clientOrderId`；兩者皆無則 Gateway 自動產生 UUID。

#### Request Body

```json
{
  "symbol": "BTCUSDT",
  "side": "BUY",
  "quantity": 0.5,
  "price": 65000.00
}
```

| 欄位 | 型別 | 必填 | 約束 |
|------|------|------|------|
| symbol | string | 是 | 交易標的；同時為 Kafka partition key |
| side | string | 是 | `BUY` / `SELL` |
| quantity | number | 是 | > 0 |
| price | number | 是 | > 0 |
| clientOrderId | string | 否 | 可選；優先使用 Header `Idempotency-Key` |

#### Response 202 Accepted

```json
{
  "commandId": "550e8400-e29b-41d4-a716-446655440000",
  "clientOrderId": "demo-order-001",
  "status": "ACCEPTED",
  "message": "Order queued for processing",
  "pollUrl": "/api/v1/orders?clientOrderId=demo-order-001",
  "acceptedAt": "2026-07-06T13:00:00Z"
}
```

| 欄位 | 說明 |
|------|------|
| commandId | 本筆 Kafka 訊息的 UUID，用於日誌追蹤 |
| clientOrderId | 業務冪等 ID |
| pollUrl | 輪詢路徑（相對路徑，接 Gateway base URL） |
| status | 固定 `ACCEPTED` |

#### Response 429 Too Many Requests

```json
{
  "errorCode": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests, retry later"
}
```

Header：`Retry-After: 1`

#### Response 503 Service Unavailable

Kafka 發送失敗時回傳，表示指令未能排入佇列。

#### Response 400 Bad Request

欄位驗證失敗（`@Valid`）。

---

## 2. 輪詢與查詢（Gateway 代理）

### GET `/api/v1/orders?clientOrderId={id}`

輪詢非同步下單結果。Gateway 以 Round-Robin 代理至 Engine。

**範例**

```http
GET /api/v1/orders?clientOrderId=demo-order-001
```

**Response 200 OK**（處理完成）

```json
{
  "id": 1,
  "clientOrderId": "demo-order-001",
  "symbol": "BTCUSDT",
  "side": "BUY",
  "quantity": 0.5,
  "price": 65000.00,
  "filledQuantity": 0.5,
  "status": "FILLED",
  "createdAt": "2026-07-06T13:00:01Z",
  "updatedAt": "2026-07-06T13:00:02Z"
}
```

**訂單狀態值**

| status | 意義 |
|--------|------|
| NEW | 已建立，尚未成交 |
| PARTIALLY_FILLED | 部分成交 |
| FILLED | 全部成交 |
| REJECTED | 風控拒絕 |
| CANCELLED | 已取消 |

> 輪詢時若 Engine 尚未處理完，可能短暫回 404；Client 應間隔重試（建議 500ms～1s）。

### GET `/api/v1/orders/{orderId}`

依資料庫主鍵查單筆訂單。

### GET `/api/v1/orders`

查詢訂單列表（支援分頁參數，依 Engine 實作）。

### GET `/api/v1/positions`

查詢所有持倉。

**Response 200 範例**

```json
[
  {
    "symbol": "BTCUSDT",
    "quantity": 0.5,
    "avgPrice": 65000.00,
    "unrealizedPnl": 0.00
  }
]
```

### GET `/api/v1/pnl`

查詢損益摘要。

### GET `/api/v1/trades`

查詢成交紀錄列表。

### GET `/api/v1/orders/{id}/events`

查詢訂單審計事件鏈（RECEIVED → RISK_CHECK → APPROVED/REJECTED → FILLED）。

---

## 3. 訂單操作（Gateway 代理）

### POST `/api/v1/orders/{id}/fill`

手動補成交（測試或管理用途）。適用於 `PARTIALLY_FILLED` 狀態的訂單。

### PATCH `/api/v1/orders/{id}/cancel`

取消訂單。僅 `NEW` / `PARTIALLY_FILLED` 可取消。

---

## 4. Engine 同步下單（測試用）

### POST `http://localhost:8081/api/v1/orders`

不經 Kafka，同步執行風控與撮合，直接回 201。

```http
POST http://localhost:8081/api/v1/orders
Content-Type: application/json

{
  "symbol": "BTCUSDT",
  "side": "BUY",
  "quantity": 0.5,
  "price": 65000,
  "clientOrderId": "sync-test-001"
}
```

**Response 201 Created** — 訂單已寫入 DB。

> 生產環境建議一律走 Gateway 非同步路徑（202）。

---

## 5. 錯誤回應格式

Engine 使用 RFC 7807 風格 JSON（`GlobalExceptionHandler`）：

```json
{
  "type": "about:blank",
  "title": "Risk Rejected",
  "status": 422,
  "errorCode": "RISK_POSITION_LIMIT",
  "detail": "Position limit exceeded for BTCUSDT",
  "ruleCode": "R001"
}
```

### HTTP 狀態碼對照

| HTTP | 情境 | errorCode 範例 |
|------|------|----------------|
| 400 | 欄位驗證失敗 | `VALIDATION_FAILED` |
| 404 | 訂單/持倉不存在 | `ORDER_NOT_FOUND` |
| 405 | 方法不允許 | `METHOD_NOT_ALLOWED` |
| 409 | 冪等重複下單 | `DUPLICATE_ORDER` |
| 422 | 風控拒絕 | `RISK_*` |
| 429 | Gateway 限流 | `RATE_LIMIT_EXCEEDED` |
| 503 | Kafka 發送失敗 | — |
| 500 | 內部錯誤 | `INTERNAL_ERROR` |

### 風控 errorCode 對照

| errorCode | 規則碼 | 說明 |
|-----------|--------|------|
| `RISK_POSITION_LIMIT` | R001 | 單標的持倉上限 |
| `RISK_EXPOSURE_LIMIT` | R002 | 總曝險上限 |
| `RISK_INVALID_ORDER` | R003 | 數量/價格不合法 |
| `DUPLICATE_ORDER` | R004 | 重複 clientOrderId |
| `RISK_VOLATILITY_LIMIT` | R005 | 極端波動拒絕 |
| `RISK_OVERTRADING` | R006 | 過度交易 |
| `RISK_MARKET_CHOPPY` | R007 | 市場震盪不適合 |
| `RISK_TREND_WEAK` | R008 | 趨勢不足 |
| `RISK_SIGNAL_NOISY` | R009 | 訊號雜訊 |

> R010（VolatilityAdjustRule）為**縮量**而非拒絕，不產生 422。

---

## 6. 典型呼叫流程

### 非同步下單 + 輪詢

```text
1. POST /api/v1/orders  (Gateway :8080)
   Header: Idempotency-Key: demo-001
   → 202 + pollUrl

2. GET /api/v1/orders?clientOrderId=demo-001  (Gateway :8080)
   → 重試直到 status = FILLED 或 REJECTED

3. GET /api/v1/positions  (Gateway :8080)
   → 200 持倉列表
```

### cURL 範例

```bash
# 下單
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
  -d '{"symbol":"BTCUSDT","side":"BUY","quantity":0.5,"price":65000}'

# 輪詢
curl "http://localhost:8080/api/v1/orders?clientOrderId=demo-001"

# 持倉
curl http://localhost:8080/api/v1/positions
```

---

## 7. Kafka 訊息契約（寫入路徑）

Gateway 發送至 topic `order.commands` 的 payload（詳見主規格書 §5）：

```json
{
  "commandId": "uuid",
  "clientOrderId": "demo-order-001",
  "symbol": "BTCUSDT",
  "side": "BUY",
  "quantity": 0.5,
  "price": 65000.00,
  "submittedAt": "2026-07-06T13:00:00Z",
  "sourceGateway": "gateway-1"
}
```

---

## 8. 相關文件

| 文件 | 用途 |
|------|------|
| [`APIGatewayMQ 規格書.md`](APIGatewayMQ%20規格書.md) | 主規格（權威） |
| [`docs/功能流程說明.md`](docs/功能流程說明.md) | 各 API 流程圖 |
| [`docs/專案引導教學.html`](docs/專案引導教學.html) | 互動架構圖 |
| Trading System MVP 規格書 §4 | Engine 風控與狀態機細節 |

---

*最後更新：2026-07-07*
