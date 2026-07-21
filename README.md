# APIGatewayMQ — 高併發交易系統（Gateway → Kafka → 多副本 Engine → PostgreSQL）

> **專案目的（與 Trading System MVP 分開）：**  
> 本專案專注 **高併發、削峰、多副本、監控**；  
> [Trading System MVP](../Trading%20System%20MVP) 專注 **交易業務、風控、狀態機、測試**。  
> 建議先學 MVP，再學本專案。

## 文件入口

| 文件 | 說明 |
|------|------|
| [APIGatewayMQ 規格書.md](APIGatewayMQ%20規格書.md) | **主規格書（權威）** |
| [API規格書.md](API規格書.md) | API 端點、錯誤碼、範例 |
| [docs/architecture.md](docs/architecture.md) | 分層與模組（可執行摘要） |
| [docs/codeGraphic.html](docs/codeGraphic.html) | Tab 式架構圖（非同步／限流／Engine／模組） |
| [docs/testing.md](docs/testing.md) | 驗證入口／DoD |
| [docs/資料庫設計.md](docs/資料庫設計.md) | 表／Entity／環境 |
| [docs/驗證設計.md](docs/驗證設計.md) | 限流／錯誤碼分層 |
| [docs/測試與CI.md](docs/測試與CI.md) | Case ID、CI 腳本 |
| [docs/架構學習導引.md](docs/架構學習導引.md) | 學習路線 |
| [docs/專案引導教學.html](docs/專案引導教學.html) | 互動架構圖 |
| [docs/初學者學習說明書.md](docs/初學者學習說明書.md) | 第一次跑起來 |
| [CLAUDE.md](CLAUDE.md) | AI／工程薄規則（EOS 0.1.4） |

## 架構

```text
Client
  │
  ▼
API Gateway (:8080)  ──限流(Redis)──► Kafka (order.commands)
  │                                        │
  │ GET/查詢代理                           ├──► Engine-1 (:8081) ──┐
  └──────────────────────────────────────  └──► Engine-2 (:8082) ──┴──► PostgreSQL
                                                    Consumer Group
Prometheus (:9090) + Grafana (:3000) ◄── Actuator metrics
```

## 快速開始

### 前置

- Docker Desktop
- JDK 21（本地開發時）

### 一鍵啟動（Docker 全棧）

```powershell
cd "D:\ClaudeCode\APIGatewayMQ"
.\scripts\start.ps1
```

### 本地開發（基礎設施 Docker + 本機 Java）

```powershell
# 1. 啟動 Kafka / PostgreSQL / Redis
docker compose up -d postgres redis zookeeper kafka

# 2. 建置
.\gradlew.bat :engine:bootRun --args="--server.port=8081"
# 另開終端
.\gradlew.bat :engine:bootRun --args="--server.port=8082"
# 另開終端
.\gradlew.bat :gateway:bootRun
```

## API 流程

### 非同步下單（經 Gateway + MQ）

```http
POST http://localhost:8080/api/v1/orders
Content-Type: application/json
Idempotency-Key: demo-order-001

{"symbol":"BTCUSDT","side":"BUY","quantity":0.5,"price":65000}
```

回應 `202 Accepted`：

```json
{
  "commandId": "...",
  "clientOrderId": "demo-order-001",
  "status": "ACCEPTED",
  "pollUrl": "/api/v1/orders?clientOrderId=demo-order-001"
}
```

輪詢結果（Gateway 代理至 Engine）：

```http
GET http://localhost:8080/api/v1/orders?clientOrderId=demo-order-001
```

### 查詢 / 補成交 / 取消

經 Gateway 代理至 Engine（Round-Robin）。例如：

- `GET /api/v1/orders`
- `GET /api/v1/positions`
- `POST /api/v1/orders/{id}/fill`
- `PATCH /api/v1/orders/{id}/cancel`

## 模組說明

| 模組 | 說明 |
|------|------|
| `common` | Kafka 訊息契約 `OrderCommandMessage` |
| `gateway` | WebFlux 入口、Redis 限流、Kafka Producer、讀 API 代理 |
| `engine` | Kafka Consumer + MVP 交易邏輯（風控/狀態機/持倉） |

## 一致性設計

- **冪等**：`Idempotency-Key` → `client_order_id` UNIQUE（MVP R004）
- **有序**：Kafka partition key = `symbol`，同標的順序處理
- **削峰**：Gateway 202 快速回應，Engine Consumer Group 水平擴展
- **限流**：Gateway Redis 固定窗口，超過回 `429`

## 監控

| 服務 | URL |
|------|-----|
| Gateway Health | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |

## 測試

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew.bat check
```

## 壓力測試

```powershell
.\scripts\load-test.ps1 -Requests 200 -Concurrency 20
```

## 與 Trading System MVP 關係

| 專案 | 學什麼 |
|------|--------|
| **Trading System MVP** | 下單流程、RiskEngine、狀態機、PnL、場景測試 |
| **APIGatewayMQ（本專案）** | Gateway 限流、Kafka 削峰、Consumer Group、Prometheus |

- 兩個 repo **獨立**，互不依賴，各自可單獨跑
- Engine 內含交易邏輯，是為了示範 **MQ Consumer 如何處理訂單**

## 停止

```powershell
docker compose down
```

> Docs standard: EngineeringOS eos-minimal @ 0.1.4 — `knowledge/documentation.md`
