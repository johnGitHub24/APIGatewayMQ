# 測試與 CI — APIGatewayMQ

> 開發順序：**資料表 → 測試案例 → 功能實作**（對齊 houseHub / MVP 方法論）  
> Case ID 與 [`專案引導教學.html`](專案引導教學.html) §12 一致。

---

## 測試分層

| 層級 | Tag | Gradle 任務 | 需 DB/Kafka | 模組 |
|------|-----|-------------|-------------|------|
| 單元測試 | `@Tag("unit")` 或無 tag | `gradlew :engine:test` / `:gateway:test` / `:common:test` | 否 | common / engine / gateway |
| 整合測試 | `@Tag("integration")` | `gradlew :engine:integrationTest` / `:gateway:integrationTest` | H2 + EmbeddedKafka | engine / gateway |
| OpenAPI 匯出 | `@Tag("openapi")` | `gradlew :engine:exportOpenApi` / `:gateway:exportOpenApi` | 否 | engine / gateway |
| 全專案 | — | `gradlew check` | 同上 | 全部 |

> Windows 需 JDK 21。可執行 `. .\scripts\env.ps1` 自動設定 `JAVA_HOME`。Gradle test 已設定 `-Djdk.attach.allowAttachSelf=true`（Mockito 相容）。

### 分層示意

```text
gradlew check
├── :common:test
├── :gateway:test          ← 單元（Mock Redis/Kafka）
├── :gateway:integrationTest ← EmbeddedKafka
├── :engine:test           ← 單元（MockMvc、RiskEngine）
└── :engine:integrationTest  ← H2 + EmbeddedKafka + 全鏈路
```

---

## 資料表（先建立、先驗證）

腳本：`docs/sql/schema.sql`  
整合測試：`DatabaseSchemaIntegrationTest.DB_001_allFourTablesExist`

| 表 | 用途 |
|----|------|
| orders | 訂單主檔（`client_order_id` UNIQUE） |
| trades | 成交紀錄 |
| positions | 持倉 |
| order_events | 審計日誌 |

---

## Case ID 對照（主表）

| Case ID | 類型 | 模組 | 測試重點 | 測試類別 |
|---------|------|------|----------|----------|
| COMMON-001 | 單元 | common | JSON 序列化 round-trip | OrderCommandMessageSerializationTest |
| COMMON-002 | 單元 | common | 必填欄位驗證 | OrderCommandMessageSerializationTest |
| DB-001 | 整合 | engine | 四張表存在 | DatabaseSchemaIntegrationTest |
| GW-001 | 單元 | gateway | Producer 正確發 Kafka | OrderCommandProducerTest |
| GW-002 | 單元 | gateway | Controller 回 202 結構 | OrderSubmitControllerTest |
| GW-003 | 整合 | gateway | POST 後 topic 有訊息 | GatewayKafkaIntegrationTest |
| GW-004 | 單元 | gateway | 限流通過 | RateLimitWebFilterTest |
| GW-005 | 單元 | gateway | 超限回 429 | RateLimitWebFilterTest |
| GW-006 | 單元 | gateway | 請求參數驗證 | OrderSubmitControllerValidationTest |
| ENGINE-MQ-001 | 整合 | engine | Consumer 消費後訂單成交 | OrderCommandConsumerIntegrationTest |
| ENGINE-MQ-002 | 整合 | engine | 重複消費冪等 | OrderCommandConsumerIntegrationTest |
| ORDER-001~006 | 整合 | engine | 下單業務（自 MVP） | PlaceOrderIntegrationTest |
| ORDER-007 | 整合 | engine | 交易回滾 | OrderRollbackIntegrationTest |
| SCN-001~005 | 整合 | engine | 市場場景 CHOP/HIGHVOL | ScenarioIntegrationTest |
| LOG-001~003 | 整合 | engine | 審計日誌 | LogIntegrationTest |

### 補充 Case ID（Engine 單元／整合）

| Case ID | 類型 | 模組 | 測試類別 | 說明 |
|---------|------|------|----------|------|
| ORDER-001/003 | 單元 | engine | OrderControllerTest | WebMvc 層下單／驗證 |
| ORDER-003 | 單元 | engine | CreateOrderRequestValidationTest | DTO 驗證 |
| R001~R010 | 單元 | engine | RiskEngineTest | 風控規則鏈 |
| — | 單元 | engine | ExecutionEngineTest | 全量／部分成交 |
| — | 單元 | engine | PnLCalculatorTest | 未實現損益計算 |
| — | 單元 | engine | PositionControllerTest | 持倉 API |
| — | 單元 | engine | PnLControllerTest | PnL API |
| R007/R010 | 整合 | engine | AdvancedRiskIntegrationTest | CHOPPY / HIGHVOL |
| — | 整合 | engine | TradeQueryIntegrationTest | 下單後查 trades |
| partialFill/cancel | 整合 | engine | PlaceOrderIntegrationTest | 部分成交／取消 |

### Case ID → 功能流程對照

| 驗證什麼 | Case ID | 對應文件 |
|----------|---------|----------|
| 非同步下單 202 | GW-002, GW-003 | 功能流程說明 §1 |
| Kafka 訊息契約 | COMMON-001 | 規格書 §5 |
| 限流 429 | GW-004/005 | 功能流程說明 §6 |
| Consumer 全鏈路 | ENGINE-MQ-001 | 功能流程說明 §2 |
| 冪等 409 | ENGINE-MQ-002, ORDER-* | API規格書 §5 |
| 風控 422 | SCN-*, R* | 架構文件 §風控 |

---

## Fixture 目錄

```text
docs/test-data/
├── placeOrder/              ← 與 MVP 共用（Engine 直接下單）
│   ├── ORDER-001-SUCCESS.json
│   └── ...
├── gateway/                 ← Gateway 專用
│   ├── GW-ORDER-001-SUCCESS.json
│   └── GW-MQ-001-COMMAND.json
```

使用方式：測試類別透過 `OrderTestFixtures` / `GatewayTestFixtures` 載入 JSON。

---

## 本機執行

```powershell
cd "D:\ClaudeCode\APIGatewayMQ"
. .\scripts\env.ps1

# 全專案驗證（等同 CI）
.\gradlew.bat check

# 或使用包裝腳本
.\scripts\check.ps1
```

### 模組別執行

```powershell
.\gradlew.bat :common:test
.\gradlew.bat :gateway:test
.\gradlew.bat :gateway:integrationTest
.\gradlew.bat :engine:test
.\gradlew.bat :engine:integrationTest
```

### 匯出 OpenAPI

```powershell
.\scripts\export-openapi.ps1
# 產出 docs/openapi-gateway.yaml、docs/openapi-engine.yaml
```

> 自動化測試使用 **H2 + EmbeddedKafka**，不需 Docker。

---

## Docker 驗證（需 Docker Desktop）

| 腳本 | 用途 | 對應 Case |
|------|------|-----------|
| `.\scripts\start.ps1` | 建置 + compose up | 部署冒煙 |
| `.\scripts\smoke-test.ps1` | 下單 + 輪詢 | GW-002 端到端 |
| `.\scripts\verify-docker.ps1` | start + smoke | 全棧 DoD |
| `.\scripts\load-test.ps1` | 壓力測試 | GW-004/005 實戰 |

```powershell
.\scripts\verify-docker.ps1
.\scripts\load-test.ps1 -Requests 200 -Concurrency 20
```

---

## CI（GitHub Actions）

檔案：`.github/workflows/ci.yml`

| 項目 | 設定 |
|------|------|
| 觸發 | push/PR → `main`、`master`、`develop` |
| JDK | 21 (Temurin) |
| 指令 | `./gradlew check --no-daemon` |
| Artifact | `**/build/reports/tests/`、`**/build/test-results/` |

### CI 與本機對照

```text
本機 gradlew check  ≈  CI job test
```

### 未來擴充（參考 TradingKubernetes）

完整 CI/CD pipeline 範本（SonarQube、Docker Build、Trivy）見 [`TradingKubernetes/ci/examples/apigatewaymq-ci-cd.yml`](../TradingKubernetes/ci/examples/apigatewaymq-ci-cd.yml)。

---

## DoD 檢查清單

- [ ] `gradlew check` 全綠（unit + integration）
- [ ] Gateway POST 回 202，Kafka topic 有訊息（GW-003）
- [ ] Engine Consumer 處理後 DB 有訂單（ENGINE-MQ-001）
- [ ] 限流超過閾值回 429（GW-005）
- [ ] 冪等鍵重複不產生第二筆訂單（ENGINE-MQ-002）
- [ ] Docker 全棧可啟動（`.\scripts\verify-docker.ps1`）

---

## 擴充 SOP

```text
1. docs/sql/schema.sql（若改表）
2. docs/test-data/ 新增 fixture JSON
3. 寫失敗測試（unit / integration），標註 Case ID
4. 實作功能
5. gradlew check 全綠
6. 更新本文件 Case ID 表
7. （可選）更新 專案引導教學.html §12
```

### 新增 Gateway 功能範例

```text
1. 在 docs/test-data/gateway/ 加 fixture
2. 寫 GW-00X 測試（先紅燈）
3. 實作 Controller / Service
4. gradlew :gateway:check 全綠
5. 更新 API規格書.md
```

---

## 相關文件

| 文件 | 用途 |
|------|------|
| [`APIGatewayMQ 規格書.md`](../APIGatewayMQ%20規格書.md) §6 | 權威測試規格 |
| [`專案引導教學.html`](專案引導教學.html) §12 | 互動 Case ID 表 |
| [`功能流程說明.md`](功能流程說明.md) | 流程與 Case 對照 |
| `測試規格書.md` | houseHub 方法論參考 |

---

*最後更新：2026-07-07*
