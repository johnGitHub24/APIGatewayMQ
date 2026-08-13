# APIGatewayMQ — 高併發交易系統（Gateway → Kafka → 多副本 Engine → PostgreSQL）

> **專案目的（與 Trading System MVP 分開）：**  
> 本專案專注 **高併發、削峰、多副本、監控**；  
> [Trading System MVP](../Trading%20System%20MVP) 專注 **交易業務、風控、狀態機、測試**。  
> 建議先學 MVP，再學本專案。

繼承 EngineeringOS eos-minimal @ **0.1.10**。

## 文件入口

單一入口：本 README。衝突以主規格為準。

| 文件 | 說明 |
|------|------|
| [APIGatewayMQ 規格書.md](APIGatewayMQ%20規格書.md) | API 契約 |
| [docs/architecture.md](docs/architecture.md) | 分層與模組 |
| [docs/codeGraphic.html](docs/codeGraphic.html) | 架構圖（非權威） |
| [docs/testing.md](docs/testing.md) | 測試／Case／check |
| [docs/資料庫設計.md](docs/資料庫設計.md) | 資料庫 |
| [docs/驗證設計.md](docs/驗證設計.md) | 驗證／權限 |
| [CLAUDE.md](CLAUDE.md) | AI 薄規則 |
| [scripts/README.md](scripts/README.md) | 驗證／啟動腳本 |

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

## 快速開始（Pure）

```powershell
.\scripts\check.ps1
.\gradlew.bat :gateway:bootRun
```

另開終端跑 Engine：

```powershell
.\gradlew.bat :engine:bootRun
```

IntelliJ：Open **專案根** → SDK 21 → Gradle Sync → Gradle 窗 **`:gateway:bootRun`**（Engine 同理 `:engine:bootRun`）。

`local` profile（預設）用 H2，不必 Docker。驗證與 CI 同一入口：`.\scripts\check.ps1`。

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

## 可選：Docker 全棧

本機學習用 Pure（H2 + `bootRun`）即可。若要 Kafka／Redis／PostgreSQL／多副本 Engine，先開 Docker Desktop，再：

```powershell
docker compose up -d
```

`SPRING_PROFILES_ACTIVE=docker` 後以 Gradle `bootRun` 或 compose 內服務連同一套基礎設施。停止：`docker compose down`。

## 與 Trading System MVP 關係

| 專案 | 學什麼 |
|------|--------|
| **Trading System MVP** | 下單流程、RiskEngine、狀態機、PnL、場景測試 |
| **APIGatewayMQ（本專案）** | Gateway 限流、Kafka 削峰、Consumer Group、Prometheus |

- 兩個 repo **獨立**，互不依賴，各自可單獨跑
- Engine 內含交易邏輯，是為了示範 **MQ Consumer 如何處理訂單**

> Docs standard: EngineeringOS eos-minimal @ 0.1.10 — `knowledge/documentation.md`

