# APIGatewayMQ 規格書

> **本文件為 APIGatewayMQ 的唯一主規格書。**  
> 涵蓋：架構、資料庫、API 契約、Kafka 訊息、測試、驗收標準。  
> 開發、測試、面試準備均以本文件為準。

---

## 第 0 章　文件體系與參考來源

### 0.1 本專案文件（權威）

| 文件 | 用途 |
|------|------|
| **`APIGatewayMQ 規格書.md`** | **主規格書（本文件）** |
| `APIGatewayMQ 架構（Spring Boot）.md` | 架構深度說明、Gateway/Kafka/Engine 哲學 |
| `docs/architecture.md` | 技術架構學習地圖（推薦起手式） |
| `docs/codeGraphic.html` | 可點選的互動架構與流程圖 |
| `docs/architecture.md` | 每個 API「做什麼、怎麼跑」 |
| `docs/architecture.md` | 環境、啟動、操作步驟 |
| `API規格書.md` | API 端點完整參考（錯誤碼、範例） |
| `docs/testing.md` | Case ID 對照、CI 與腳本 |

### 0.2 外部參考（非本專案規格）

| 來源 | 採用範圍 | **不採用** |
|------|----------|------------|
| **Trading System MVP** | 交易業務邏輯、Risk Engine、DB schema、ORDER/SCN/LOG 案例 | 單體同步下單架構 |
| **houseHub 測試規格書** | 測試**方法論**：fixture 分層、Case ID 命名、整合測試 SOP | API 路由、`000000` 錯誤碼、Grails/Spock |

```text
Trading System MVP  ──業務參考──►  Engine 模組（風控/狀態機）
houseHub 測試規格書  ──方法論──►  本文件 §第 6 章（測試）
本文件               ──唯一權威──►  Gateway API / Kafka / 案例 / 驗收
```

### 0.3 當前成熟度

| Level | 名稱 | 狀態 |
|-------|------|------|
| ⚪ L0 | 規格階段 | ✅ 完成 |
| 🟡 L1 | 能跑 | ✅ **已達成**（Docker 全棧） |
| 🟠 L2 | 流程完整 | ✅ **已達成**（Gateway→Kafka→Engine→DB） |
| 🔴 L3 | 接近 production | ✅ **已達成**（限流、多副本、Prometheus/Grafana） |

### 0.4 與 Trading System MVP 的關係

| 專案 | 學什麼 | 架構 |
|------|--------|------|
| **Trading System MVP** | 下單流程、RiskEngine、狀態機、PnL | 單體 Spring Boot |
| **APIGatewayMQ（本專案）** | Gateway 限流、Kafka 削峰、Consumer Group、監控 | 多模組 + MQ |

兩個 repo **獨立**，互不依賴，各自可單獨跑。

### 0.5 核心名詞速查

| 名詞 | 說明 |
|------|------|
| 削峰 | 尖峰流量先進 Kafka 排隊，Engine 按能力消化 |
| 冪等 | `Idempotency-Key` → `client_order_id` UNIQUE（R004） |
| Consumer Group | `trading-engine`；engine-1/2 同組分攤 partition |
| Partition Key | `symbol`；同標的訊息順序處理 |
| pollUrl | 202 回應中的輪詢路徑 |
| fail-open | Redis 故障時限流放行，避免全站不可用 |

---

## 第 1 章　系統範圍

### 1.1 核心功能

| # | 功能 | 說明 |
|---|------|------|
| 1 | 非同步下單 | Gateway 202 快速回應 → Kafka → Engine 處理 |
| 2 | 限流削峰 | Redis 固定窗口限流，超過回 429 |
| 3 | 訂單查詢代理 | Gateway Round-Robin 代理至 Engine |
| 4 | 交易引擎 | 風控、狀態機、持倉、PnL（繼承 MVP 邏輯） |
| 5 | 多副本擴展 | Engine Consumer Group 水平擴展 |
| 6 | 監控 | Actuator + Prometheus + Grafana |

### 1.2 不在範圍

- 真實交易所撮合
- 生產級 Kafka 叢集運維（MVP 用單 broker）
- 分散式事務（Saga / 2PC）

### 1.3 技術棧

| 層 | 技術 |
|----|------|
| Gateway | Spring Boot 3.2、WebFlux、Redis Reactive、Kafka Producer |
| Engine | Spring Boot 3.2、Web MVC、JPA、Kafka Consumer |
| Common | Kafka 訊息契約 DTO |
| 資料庫 | PostgreSQL（Docker）/ H2（測試） |
| 訊息佇列 | Kafka（EmbeddedKafka 測試） |
| 快取/限流 | Redis |
| 建置 | Gradle 多模組（`test` + `integrationTest`） |
| 測試 | JUnit 5、WebTestClient、MockMvc、EmbeddedKafka |
| 監控 | Prometheus、Grafana |
| CI | GitHub Actions |

### 1.4 啟動方式

```powershell
.\scripts\check.ps1
.\gradlew.bat :gateway:bootRun
# 另開終端
.\gradlew.bat :engine:bootRun
```

IntelliJ：專案根 → Gradle Sync → **`:gateway:bootRun`**／**`:engine:bootRun`**。預設 `local`（H2），不必 Docker。

可選全棧（需 Docker Desktop）：`docker compose up -d`，再以 `SPRING_PROFILES_ACTIVE=docker` 啟動。

---

## 第 2 章　分層架構

### 2.1 模組結構

```text
APIGatewayMQ/
├── common/          ← Kafka 訊息契約（OrderCommandMessage）
├── gateway/         ← WebFlux 入口 (:8080)
│   ├── web/         ← OrderSubmitController、EngineProxyController
│   ├── service/     ← OrderCommandProducer、EngineProxyService
│   └── filter/      ← RateLimitWebFilter
└── engine/          ← 交易引擎 (:8081)
    ├── api/         ← REST Controller（同步下單/查詢）
    ├── engine/      ← OrderCommandConsumer（Kafka 入口）
    ├── application/ ← TradingService、RiskEngine、ExecutionEngine
    ├── domain/      ← OrderStatus、ErrorCodes
    ├── infrastructure/ ← JPA Entity、Repository
    └── dto/         ← API 請求/回應
```

### 2.2 請求路徑

```text
【非同步下單】
Client → Gateway POST /api/v1/orders
       → 202 Accepted + pollUrl
       → Kafka order.commands (key=symbol)
       → Engine OrderCommandConsumer
       → TradingService.placeOrder()
       → PostgreSQL

【查詢/補成交/取消】
Client → Gateway GET/PATCH/POST /api/v1/**
       → EngineProxyService (Round-Robin)
       → Engine REST API
       → PostgreSQL
```

### 2.3 一致性設計

| 機制 | 實作 |
|------|------|
| 冪等 | `Idempotency-Key` → `client_order_id` UNIQUE（R004） |
| 有序 | Kafka partition key = `symbol` |
| 削峰 | Gateway 202 快速回應 |
| 限流 | Redis 固定窗口 1 秒 |
| 擴展 | Engine Consumer Group 多副本 |

---

## 第 3 章　資料庫規格

與 Trading System MVP 共用 schema，腳本：`docs/sql/schema.sql`

| 表 | 用途 |
|----|------|
| orders | 訂單主檔 |
| trades | 成交紀錄 |
| positions | 持倉 |
| order_events | 審計日誌 |

整合測試：`DatabaseSchemaIntegrationTest.DB_001_allFourTablesExist`

---

## 第 4 章　API 規格

### 4.1 Gateway 專用端點

#### POST `/api/v1/orders` — 非同步下單（Gateway）

**Request Header**

```http
POST /api/v1/orders HTTP/1.1
Content-Type: application/json
Idempotency-Key: demo-order-001
```

**Request Body**

```json
{
  "symbol": "BTCUSDT",
  "side": "BUY",
  "quantity": 0.5,
  "price": 65000.00
}
```

**Response 202 Accepted**

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

**Response 429 Too Many Requests**（限流）

```json
{
  "errorCode": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests, retry later"
}
```

**Response 503 Service Unavailable**（Kafka 發送失敗）

---

### 4.2 Engine 端點（經 Gateway 代理或直接呼叫）

| # | 功能 | Method | 路徑 | 回應 |
|---|------|--------|------|------|
| 1 | 下單（同步，直連 Engine） | POST | `/api/v1/orders` | 201 |
| 2 | 查詢單筆 | GET | `/api/v1/orders/{orderId}` | 200 |
| 3 | 查詢列表 | GET | `/api/v1/orders` | 200 |
| 4 | 依 clientOrderId 查詢 | GET | `/api/v1/orders?clientOrderId=` | 200 |
| 5 | 持倉列表 | GET | `/api/v1/positions` | 200 |
| 6 | PnL 摘要 | GET | `/api/v1/pnl` | 200 |
| 7 | 成交列表 | GET | `/api/v1/trades` | 200 |
| 8 | 補成交 | POST | `/api/v1/orders/{id}/fill` | 200 |
| 9 | 取消訂單 | PATCH | `/api/v1/orders/{id}/cancel` | 200 |
| 10 | 訂單事件 | GET | `/api/v1/orders/{id}/events` | 200 |

> Engine API 完整端點、錯誤碼、範例見 **`API規格書.md`**。  
> 風控規則與狀態機與 Trading System MVP 規格書第 4、5 章一致。

### 4.3 錯誤碼摘要

| HTTP | errorCode | 情境 |
|------|-----------|------|
| 429 | `RATE_LIMIT_EXCEEDED` | Gateway 限流 |
| 503 | — | Kafka 發送失敗 |
| 400 | `VALIDATION_FAILED` | 欄位驗證 |
| 409 | `DUPLICATE_ORDER` | 冪等重複（R004） |
| 422 | `RISK_*` | 風控拒絕（R001~R009） |
| 404 | `ORDER_NOT_FOUND` | 訂單不存在 |

### 4.4 風控規則鏈（Engine）

| 順序 | 規則碼 | 類別 | 結果 |
|------|--------|------|------|
| 1 | R003 | SizeValidationRule | 非法數量/價格 → 拒絕 |
| 2 | R004 | DuplicateCheckRule | 重複 clientOrderId → 409 |
| 3 | R007 | MarketSuitabilityRule | 市場不適合 → 拒絕 |
| 4 | R008 | TrendConfirmationRule | 趨勢不足 → 拒絕 |
| 5 | R009 | SignalValidationRule | 訊號雜訊 → 拒絕 |
| 6 | R010 | VolatilityAdjustRule | 高波動 → **縮量**（非拒絕） |
| 7 | R001 | PositionLimitRule | 持倉上限 → 拒絕 |
| 8 | R002 | ExposureLimitRule | 曝險上限 → 拒絕 |
| 9 | R005 | VolatilityRule | 極端波動 → 拒絕 |
| 10 | R006 | OvertradingRule | 過度交易 → 拒絕 |

### 4.5 訂單狀態機

```text
NEW → PARTIALLY_FILLED → FILLED
  ↓         ↓
REJECTED  CANCELLED
```

---

## 第 5 章　Kafka 訊息規格

### 5.1 Topic

| Topic | 用途 | Partition Key |
|-------|------|---------------|
| `order.commands` | 下單指令 | `symbol` |

### 5.2 OrderCommandMessage

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

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| commandId | string | 是 | 指令 UUID |
| clientOrderId | string | 是 | 冪等鍵（來自 Idempotency-Key） |
| symbol | string | 是 | 交易標的 |
| side | string | 是 | BUY / SELL |
| quantity | number | 是 | > 0 |
| price | number | 是 | > 0 |
| submittedAt | ISO-8601 | 是 | 提交時間 |
| sourceGateway | string | 是 | Gateway 實例 ID |

### 5.3 Producer 可靠性設定

| 設定 | 值 | 說明 |
|------|-----|------|
| acks | all | 所有副本確認 |
| enable.idempotence | true | Producer 層冪等 |
| partition key | symbol | 同標的順序處理 |

### 5.4 Consumer 設定

| 設定 | 值 | 說明 |
|------|-----|------|
| group.id | trading-engine | 多副本分攤 |
| concurrency | 3 | 每實例 3 執行緒 |
| 錯誤重試 | FixedBackOff 1s × 3 | `KafkaConsumerConfig` |

---

## 第 6 章　測試規格

### 6.1 測試分層

| 層級 | Tag | Gradle 任務 | 需 DB/Kafka | 模組 |
|------|-----|-------------|-------------|------|
| 單元測試 | `@Tag("unit")` | `gradlew :module:test` | 否 | common/gateway/engine |
| 整合測試 | `@Tag("integration")` | `gradlew :module:integrationTest` | H2 + EmbeddedKafka | gateway/engine |
| 全專案 | — | `gradlew check` | 同上 | 全部 |

### 6.2 Case ID 對照

| Case ID | 類型 | 模組 | 測試類別 |
|---------|------|------|----------|
| COMMON-001/002 | 單元 | common | OrderCommandMessageSerializationTest |
| DB-001 | 整合 | engine | DatabaseSchemaIntegrationTest |
| ORDER-001~007 | 整合 | engine | PlaceOrderIntegrationTest 等（自 MVP） |
| SCN-001~005 | 整合 | engine | ScenarioIntegrationTest |
| LOG-001~003 | 整合 | engine | LogIntegrationTest |
| ENGINE-MQ-001/002 | 整合 | engine | OrderCommandConsumerIntegrationTest |
| GW-001 | 單元 | gateway | OrderCommandProducerTest |
| GW-002 | 單元 | gateway | OrderSubmitControllerTest |
| GW-003 | 整合 | gateway | GatewayKafkaIntegrationTest |
| GW-004/005 | 單元 | gateway | RateLimitWebFilterTest |
| GW-006 | 單元 | gateway | OrderSubmitControllerValidationTest |

### 6.3 Fixture 目錄

```text
docs/test-data/
├── placeOrder/          ← Engine 直接下單（自 MVP）
├── gateway/             ← Gateway 專用
│   ├── GW-ORDER-001-SUCCESS.json
│   └── GW-MQ-001-COMMAND.json
```

### 6.4 DoD 檢查清單

- [ ] `gradlew check` 全綠（unit + integration）
- [ ] Gateway POST 回 202，Kafka topic 有訊息
- [ ] Engine Consumer 處理後 DB 有訂單
- [ ] 限流超過閾值回 429
- [ ] 冪等鍵重複不產生第二筆訂單
- [ ] （可選）Docker 全棧可啟動（`docker compose up -d`）

---

## 第 7 章　部署與監控

### 7.1 Docker Compose 服務

| 服務 | 埠 | 用途 |
|------|-----|------|
| gateway | 8080 | API 入口 |
| engine-1 / engine-2 | 8081 / 8082 | 交易引擎副本 |
| kafka | 9092 | 訊息佇列 |
| postgres | 5433 | 資料庫 |
| redis | 6379 | 限流 |
| prometheus | 9090 | 指標收集 |
| grafana | 3000 | 儀表板 |

### 7.2 健康檢查與監控

| 服務 | URL |
|------|-----|
| Gateway Health | http://localhost:8080/actuator/health |
| Gateway Metrics | http://localhost:8080/actuator/prometheus |
| Engine Health | http://localhost:8081/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| Swagger (Gateway) | http://localhost:8080/swagger-ui.html |
| Swagger (Engine) | http://localhost:8081/swagger-ui/index.html |
| Swagger 靜態文件 | `docs/swagger.html`（載入 `docs/openapi-*-live.yaml`） |

### 7.3 驗證與啟動（Pure）

| 指令 | 用途 |
|------|------|
| `.\scripts\check.ps1` | unit + integration（與 CI 同一入口） |
| `.\gradlew.bat :gateway:bootRun` | 本機 Gateway（:8080） |
| `.\gradlew.bat :engine:bootRun` | 本機 Engine（:8081，預設 H2） |
| `docker compose up -d` | （可選）Kafka／Redis／PostgreSQL／監控 |

### 7.4 關鍵類別索引

| 模組 | 類別 | 職責 |
|------|------|------|
| common | `OrderCommandMessage` | Kafka 訊息本體 |
| gateway | `OrderSubmitController` | 非同步下單 202 |
| gateway | `RateLimitWebFilter` | Redis 限流 |
| gateway | `EngineProxyService` | Round-Robin 代理 |
| engine | `OrderCommandConsumer` | Kafka 消費入口 |
| engine | `TradingService` | 交易編排核心 |
| engine | `RiskEngineImpl` | 10 條風控規則鏈 |

---

## 8. 排程 Job（Engine）

Engine 內建四支獨立排程 Job，皆以 `@Scheduled` 觸發並可透過 `@ConditionalOnProperty` 開關；亦提供 REST 端點供維運手動觸發與查詢輸出（已收錄於 Swagger）。

### 8.1 Job 一覽

| 代碼 | Job | 說明 | 預設 cron | 手動端點 |
|------|-----|------|-----------|----------|
| JOB-A | `StaleOrderTimeoutJob` | 取消逾時（預設 300 秒）未成交訂單並記錄 `CANCELLED` 事件 | `0 */5 * * * *` | `POST /api/v1/jobs/stale-order-cancellation` |
| JOB-B | `PnlSnapshotJob` | 建立當日持倉/PnL 結算快照（同日冪等） | `0 0 0 * * *` | `POST /api/v1/jobs/pnl-snapshot` |
| JOB-C | `FailedCommandRetryJob` | 重試 DLQ 失敗下單指令，退避＋超過上限標記 `DEAD` | `0 * * * * *` | `POST /api/v1/jobs/failed-command-retry` |
| JOB-D | `DataCleanupJob` | 清理過期審計事件（30 天）與終態失敗指令（7 天） | 每小時 | `POST /api/v1/jobs/cleanup` |

輸出查詢端點：`GET /api/v1/pnl-snapshots?date=` （JOB-B）、`GET /api/v1/failed-commands?status=` （JOB-C）。

### 8.2 設定（`trading.job.*`）

| 屬性 | 預設 | 說明 |
|------|------|------|
| `stale-order.enabled` / `timeout-seconds` / `batch-size` | true / 300 / 200 | JOB-A |
| `pnl-snapshot.enabled` | true | JOB-B |
| `retry.enabled` / `max-attempts` / `backoff-seconds` / `batch-size` | true / 3 / 30 / 100 | JOB-C |
| `cleanup.enabled` / `event-retention-days` / `failed-command-retention-days` / `batch-size` | true / 30 / 7 / 500 | JOB-D |

測試環境（`application-test.properties`）預設 `enabled=false`，整合測試直接呼叫 Service 驗證。

### 8.3 測試 Case ID

| 層次 | Case ID |
|------|---------|
| 單元（Service） | `JOB_STALE_001~004`、`JOB_PNL_*`、`JOB_RETRY_001~004`、`JOB_CLEAN_001~002` |
| 整合（H2 真實 Repository） | `JOB_STALE_001~003`、`JOB_PNL_001~003`、`JOB_RETRY_001/004`、`JOB_CLEAN_001/003` |
| Controller（MockMvc） | `JOB_API_001~006` |

### 8.4 新增資料表

| 資料表 | 用途 |
|--------|------|
| `pnl_snapshots` | JOB-B 每日結算快照 |
| `failed_commands` | JOB-C 失敗指令 DLQ（狀態：PENDING/SUCCEEDED/DEAD） |

---

*最後更新：2026-07-08 | 技術棧：Spring Boot 3 · Kafka · Redis · PostgreSQL · JUnit 5*
