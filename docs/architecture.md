# ⚙️📡 APIGatewayMQ 架構（Spring Boot）

> 深入說明 Gateway / Kafka / Engine 的分層哲學、關鍵類別與技術選型。  
> 建議搭配 [`docs/codeGraphic.html`](docs/codeGraphic.html) 互動閱讀；權威契約以 [`APIGatewayMQ 規格書.md`](APIGatewayMQ%20規格書.md) 為準。

---

## ① 系統總覽

**系統本質：** 限流入口 → 202 快速回應 → MQ 削峰 → Engine 風控成交 → 輪詢查結果

```
            ┌──────────────────────┐
            │      Client          │
            └─────────┬────────────┘
                      │
                      ▼
        ┌─────────────────────────────┐
        │   API Gateway (:8080)       │
        │   WebFlux + Redis 限流       │
        └──────┬──────────────┬───────┘
               │              │
        POST 202│              │ GET/PATCH 代理
               ▼              ▼
        ┌─────────────┐  ┌──────────────┐
        │   Kafka     │  │ Engine x N   │
        │order.commands│  │ (:8081/8082) │
        └──────┬──────┘  └──────┬───────┘
               │                │
               └───────┬────────┘
                       ▼
              ┌─────────────────┐
              │   PostgreSQL    │
              └─────────────────┘

Prometheus (:9090) + Grafana (:3000) ◄── Actuator metrics
```

### 技術標籤說明

| 標籤 | 在本專案做什麼 |
|------|----------------|
| **Java 21** | Gradle toolchain 鎖定 JDK 21；Docker 使用 Temurin 21 JRE |
| **Spring Boot 3** | 自動配置、依賴注入、內嵌伺服器、Actuator 監控 |
| **WebFlux Gateway** | 非阻塞 I/O（Netty）處理高併發請求 |
| **Kafka** | Gateway 與 Engine 之間的非同步緩衝層，實現削峰 |
| **Redis 限流** | 存每秒請求計數，做 Gateway 入口限流 |
| **Prometheus** | 定期拉取 `/actuator/prometheus` 的 metrics |

---

## ② 三模組架構

| 模組 | 職責 | 代表類別 |
|------|------|----------|
| **common** | Kafka 訊息契約 | `OrderCommandMessage`、`OrderAcceptedResponse`、`Topics` |
| **gateway** | WebFlux 入口、Redis 限流、Kafka Producer、讀 API 代理 | `OrderSubmitController`、`RateLimitWebFilter` |
| **engine** | Kafka Consumer + MVP 交易邏輯（風控/狀態機/持倉） | `OrderCommandConsumer`、`TradingService` |

**為何 common 獨立？** Gateway 與 Engine 都依賴同一份 Kafka 訊息格式，改一處兩邊同步，避免序列化不一致。

```text
APIGatewayMQ/
├── common/          ← Kafka 訊息契約
├── gateway/         ← WebFlux 入口 (:8080)
│   ├── web/         ← Controller
│   ├── service/     ← Producer、Proxy
│   ├── filter/      ← 限流
│   └── config/      ← Kafka、WebClient、OpenAPI
└── engine/          ← 交易引擎 (:8081/8082)
    ├── api/         ← REST Controller
    ├── engine/      ← Kafka Consumer
    ├── application/ ← 商業邏輯
    ├── domain/      ← 領域模型
    ├── infrastructure/ ← JPA
    └── dto/         ← 請求/回應
```

---

## ③ 核心名詞辭典

| 名詞 | 白話解釋 |
|------|----------|
| **削峰** | 尖峰流量先丟進 Kafka 排隊，Engine 按能力慢慢消化 |
| **冪等（Idempotent）** | 同一請求重送多次，結果與送一次相同；靠 `Idempotency-Key` + DB UNIQUE |
| **Consumer Group** | Kafka 消費者群組；同組內多副本分攤 partition |
| **Partition Key** | 決定訊息進哪個 partition；本專案用 `symbol`，保證同標的順序處理 |
| **Round-Robin** | Gateway 查詢代理輪流轉發至 engine-1、engine-2 |
| **固定窗口限流** | 每 1 秒重置計數器；超過閾值回 429 |
| **pollUrl** | 202 回應中的查詢路徑，Client 輪詢直到 FILLED / REJECTED |
| **fail-open** | Redis 故障時限流器放行請求，避免全站因 Redis 掛掉而不可用 |

### HTTP 狀態碼

| 狀態碼 | 本專案何時出現 |
|--------|----------------|
| **202 Accepted** | Gateway 收到下單、已排進 Kafka |
| **201 Created** | Engine 直連下單（測試用，同步） |
| **200 OK** | 輪詢查到訂單、查持倉、PnL 等 |
| **429** | 超過 Gateway 每秒限流閾值 |
| **409 Conflict** | 重複下單（冪等觸發 DuplicateCheckRule） |
| **422** | 風控規則拒絕下單 |
| **503** | Kafka 發送失敗 |

---

## ④ Gateway 層（WebFlux）

👉 對外唯一入口 — 系統的「閘門」

### 職責

- 非同步下單（202 Accepted）
- Redis 限流（429）
- 讀 API Round-Robin 代理至 Engine
- Kafka Producer

### 關鍵類別

| 類別 | 路徑 | 職責 |
|------|------|------|
| `OrderSubmitController` | `gateway/web/` | 接收 POST；組 `OrderCommandMessage`；發 Kafka；回 202 |
| `OrderCommandProducer` | `gateway/service/` | `kafkaTemplate.send(topic, symbol, command)` |
| `EngineProxyController` | `gateway/web/` | 代理 GET / PATCH / POST fill；**不代理 POST 下單** |
| `EngineProxyService` | `gateway/service/` | Round-Robin 選 Engine；WebClient 非阻塞轉發 |
| `RateLimitWebFilter` | `gateway/filter/` | Redis INCR 計數；超限回 429 + Retry-After |
| `KafkaProducerConfig` | `gateway/config/` | `acks=all`、`enable.idempotence=true` |
| `GatewayProperties` | `gateway/config/` | Engine URI 清單、限流閾值、instance-id |

### 為什麼回 202 而不是 201？

| 狀態碼 | 語意 | 適用場景 |
|--------|------|----------|
| **201 Created** | 資源已建立完成 | 同步下單（Engine 直連） |
| **202 Accepted** | 已排隊、尚未完成 | Gateway 非同步下單，只保證已進 Kafka |

Gateway 只負責**接收並排隊**，不等待 Engine 跑完風控+撮合+寫 DB。HTTP 連線馬上釋放，實現削峰。

---

## ⑤ Message Queue 層（Kafka）

👉 解耦 Gateway 與 Engine — 流量緩衝區

| 項目 | 值 |
|------|-----|
| Topic | `order.commands` |
| Partition Key | `symbol`（同標的順序處理） |
| Consumer Group | `trading-engine`（engine-1、engine-2 同組分攤） |
| Producer | Gateway `OrderCommandProducer` |
| Consumer | Engine `OrderCommandConsumer`（`@KafkaListener`） |

### Kafka 名詞

| 名詞 | 解釋 |
|------|------|
| **Topic** | 訊息分類的「頻道」 |
| **Partition** | Topic 內的分片；key=symbol 決定進哪個 partition |
| **Offset** | partition 內訊息序號 |
| **acks=all** | Producer 等所有副本確認才視為發送成功 |
| **enable.idempotence** | Kafka Producer 層冪等，避免網路重試造成重複訊息 |

### 訊息範例

```json
{
  "commandId": "uuid",
  "clientOrderId": "demo-001",
  "symbol": "BTCUSDT",
  "side": "BUY",
  "quantity": 0.5,
  "price": 65000,
  "submittedAt": "2026-07-06T13:00:00Z",
  "sourceGateway": "gateway-1"
}
```

---

## ⑥ Engine 層（Spring MVC）

👉 業務執行者 — 風控、成交、持倉

### 雙入口

1. **Kafka Consumer** — `OrderCommandConsumer`（非同步路徑，生產環境主路徑）
2. **REST Controller** — `OrderController`（同步路徑 / 代理查詢 / 測試除錯）

### 分層架構

| 層 | 目錄 | 職責 | 範例 |
|----|------|------|------|
| **api** | `engine/api/` | HTTP 入口 | `OrderController`、`PositionController` |
| **application** | `engine/application/` | 商業邏輯 | `TradingService`、`RiskEngine` |
| **domain** | `engine/domain/` | 領域概念 | `OrderStatus`、`ErrorCodes` |
| **infrastructure** | `engine/infrastructure/` | 持久化 | `OrderEntity`、`OrderRepository` |
| **dto** | `engine/dto/` | 資料傳輸 | `CreateOrderRequest` |

### 核心流程

```text
receive order
    ↓
RiskEngine.validate()（10 條規則鏈）
    ↓
ExecutionEngine（全量/部分成交：threshold=10, ratio=50%）
    ↓
PositionService + PnLCalculator
    ↓
OrderEventService（審計）
```

### 關鍵類別

| 類別 | 路徑 | 職責 |
|------|------|------|
| `OrderCommandConsumer` | `engine/messaging/` | `@KafkaListener` 消費；轉 `CreateOrderRequest` |
| `TradingService` | `engine/application/` | 核心編排：風控 → 建單 → 撮合 → 持倉 → 審計 |
| `RiskEngineImpl` | `engine/application/risk/` | 依 `@Order` 執行 10 條 RiskRule 鏈 |
| `ExecutionEngine` | `engine/application/` | 模擬撮合 |
| `OrderLookupController` | `engine/web/` | `GET ?clientOrderId=` 供 Gateway 輪詢 |
| `GlobalExceptionHandler` | `engine/config/` | 統一錯誤格式（409、422 等） |

### 10 條風控規則（執行順序）

| 順序 | 規則類別 | 規則碼 | 做什麼 |
|------|----------|--------|--------|
| 1 | SizeValidationRule | R003 | 數量、價格合法性驗證 |
| 2 | DuplicateCheckRule | R004 | 冪等：重複 clientOrderId 拒絕 |
| 3 | MarketSuitabilityRule | R007 | 市場是否適合交易 |
| 4 | TrendConfirmationRule | R008 | 趨勢確認 |
| 5 | SignalValidationRule | R009 | 訊號雜訊過濾 |
| 6 | VolatilityAdjustRule | R010 | 高波動降倉（縮量，非拒絕） |
| 7 | PositionLimitRule | R001 | 單標的持倉上限 |
| 8 | ExposureLimitRule | R002 | 總曝險上限 |
| 9 | VolatilityRule | R005 | 極端波動拒絕 |
| 10 | OvertradingRule | R006 | 過度交易限制 |

### 訂單狀態機

```text
NEW → PARTIALLY_FILLED → FILLED
  ↓         ↓
REJECTED  CANCELLED
```

### WebFlux vs Web MVC

| | WebFlux（Gateway） | Web MVC（Engine） |
|--|-------------------|-------------------|
| 模型 | 非阻塞、事件驅動（Mono/Flux） | 一請求一執行緒 |
| 底層 | Netty | Tomcat（內嵌） |
| 適合 | 高併發 I/O、代理、限流 | JPA 交易邏輯、複雜業務 |

---

## ⑦ 查詢代理（Round-Robin）

**為何寫入走 Kafka、讀取走代理？**

- 下單要削峰 → Kafka 緩衝
- 查詢要即時結果 → 直接 HTTP 代理到 Engine 查 DB

`EngineProxyService` 依 `gateway.engine-uris`（環境變數 `ENGINE_URI_1`、`ENGINE_URI_2`）輪流選 Engine，用 `WebClient` 非阻塞轉發。

---

## ⑧ Redis 限流

`RateLimitWebFilter` 使用**固定窗口（1 秒）**，以客戶端 IP 為 key：

```text
key = gateway:rate:{clientIp}
INCR → count==1 時 EXPIRE 1s → count > threshold → 429
```

| 名詞 | 解釋 |
|------|------|
| **WebFilter** | WebFlux 請求過濾器，在進 Controller 之前執行 |
| **Retry-After** | 429 回應 Header，建議 1 秒後重試 |
| **fail-open** | Redis 掛了時 `onErrorResume` 放行 |

限流閾值：本機預設 50 req/s；Docker 環境變數 `GATEWAY_RATE_LIMIT=100`。

---

## ⑨ Persistence Layer

| Entity | 資料表 | 重點欄位 |
|--------|--------|----------|
| `OrderEntity` | orders | `client_order_id` UNIQUE（冪等） |
| `TradeEntity` | trades | FK → orders |
| `PositionEntity` | positions | symbol UNIQUE |
| `OrderEventEntity` | order_events | 審計軌跡 |

- PostgreSQL（Docker 生產）
- H2（測試）
- 腳本：`docs/sql/schema.sql`

---

## ⑩ Observability

| 端點 | URL |
|------|-----|
| Gateway Health | http://localhost:8080/actuator/health |
| Gateway Metrics | http://localhost:8080/actuator/prometheus |
| Engine Health | http://localhost:8081/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| Swagger (Gateway) | http://localhost:8080/swagger-ui.html |
| Swagger (Engine) | http://localhost:8081/swagger-ui/index.html |

---

## ⑪ 資料流總結

```text
【寫入路徑 — 非同步下單】
Client
  → RateLimitWebFilter（Redis 計數）
  → OrderSubmitController（組 OrderCommandMessage）
  → OrderCommandProducer（spring-kafka）
  → Kafka topic: order.commands（key=symbol）
  → OrderCommandConsumer（@KafkaListener）
  → TradingService → RiskEngine → ExecutionEngine
  → JPA → PostgreSQL

【讀取路徑 — 輪詢/查詢】
Client
  → EngineProxyController
  → EngineProxyService（WebClient Round-Robin）
  → Engine OrderLookupController / OrderController
  → JPA → PostgreSQL
  → 200 回傳訂單狀態
```

---

## ⑫ Library 依賴摘要

### gateway 模組

| Library | 做什麼 |
|---------|--------|
| spring-boot-starter-webflux | 反應式 Web（Netty） |
| spring-boot-starter-data-redis-reactive | 反應式 Redis 限流 |
| spring-kafka | KafkaTemplate 發送訊息 |
| micrometer-registry-prometheus | Prometheus 指標 |
| springdoc-openapi-starter-webflux-ui | Swagger UI |

### engine 模組

| Library | 做什麼 |
|---------|--------|
| spring-boot-starter-web | 傳統 MVC REST API |
| spring-boot-starter-data-jpa | ORM 操作 PostgreSQL |
| spring-kafka | `@KafkaListener` 消費 |
| postgresql / h2 | JDBC 驅動 |
| awaitility（測試） | 非同步等待斷言 |

---

## ⑬ 與 Trading System MVP 對照

| 維度 | Trading System MVP | APIGatewayMQ |
|------|-------------------|--------------|
| 架構 | 單體 | 多模組 + MQ |
| 下單回應 | 201 同步 | 202 非同步 |
| 限流 | 無 | Redis 固定窗口 |
| 擴展 | 垂直 | Consumer Group 水平 |
| 監控 | Actuator | Actuator + Prometheus + Grafana |
| 學習重點 | 業務/風控 | 高併發/削峰/分散式 |

### 套件對照

| Gateway | Engine |
|---------|--------|
| `com.trading.gateway.web` | `com.trading.api` |
| `com.trading.gateway.service` | `com.trading.application` |
| `com.trading.gateway.filter` | `com.trading.engine.messaging` |
| `com.trading.gateway.config` | `com.trading.infrastructure` |
| `com.trading.common`（共用） | `com.trading.domain` / `dto` |

---

## ⑭ 基礎設施（Docker Compose）

| 服務 | 映像 | 埠 | 做什麼 |
|------|------|-----|--------|
| postgres | postgres:16-alpine | 5433→5432 | 正式資料庫 |
| redis | redis:7-alpine | 6379 | Gateway 限流計數器 |
| zookeeper | confluent 7.5.0 | 2181 | Kafka 叢集協調 |
| kafka | confluent 7.5.0 | 9092 | 訊息佇列 broker |
| engine-1/2 | 自建 Dockerfile | 8081/8082 | Consumer Group 成員 |
| gateway | 自建 Dockerfile | 8080 | API 入口 |
| prometheus | v2.48.0 | 9090 | 指標收集 |
| grafana | 10.2.0 | 3000 | 視覺化儀表板 |

---

*最後更新：2026-07-07 | 搭配 `docs/codeGraphic.html` 互動閱讀*
