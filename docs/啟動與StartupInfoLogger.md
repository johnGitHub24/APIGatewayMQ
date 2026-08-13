# 啟動與 StartupInfoLogger（APIGatewayMQ）

> 對齊 TradingCRUD 同套路：`ApplicationReadyEvent` → Console 印常用 URL。  
> 權威開關：`startup.info.*`（`application.yml` / `application-local.yml` / `application-docker.yml`）。

## 兩個模式

| Profile | 何時用 | DB | Kafka Listener |
|---------|--------|-----|----------------|
| **`local`（預設）** | IntelliJ 先學 Engine | H2 mem | 關閉 |
| **`docker`** | 全棧／compose | PostgreSQL | 開啟 |

IntelliJ 執行 `TradingEngineApplication` 時 **Active profiles 留空** → 自動 `local`。

## 啟動後會看到什麼

框線會依 profile 區分：

**`local`（IntelliJ 預設）— 只有 Engine 真的起來**

| 本機已可用 | URL |
|------------|-----|
| Engine Health | http://localhost:8081/actuator/health |
| Swagger (Engine) | http://localhost:8081/swagger-ui/index.html |

其餘 Gateway／Prometheus／Grafana／Swagger(Gateway) 會標成 **需 Docker 全棧**（`.\scripts\start.ps1`）。啟動全棧前請停掉本機 Engine，避免 8081 埠衝突。

**`docker`／Gateway 啟動後 — 全棧連結**

| 項目 | URL |
|------|-----|
| Gateway Health | http://localhost:8080/actuator/health |
| Gateway Metrics | http://localhost:8080/actuator/prometheus |
| Engine Health | http://localhost:8081/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000（admin/admin） |
| Swagger (Gateway) | http://localhost:8080/swagger-ui.html |
| Swagger (Engine) | http://localhost:8081/swagger-ui/index.html |

覆寫位址：`startup.info.gateway-url`／`engine-url`／`prometheus-url`／`grafana-url`

實作：

- `engine/.../config/StartupInfoLogger.java`
- `gateway/.../config/StartupInfoLogger.java`

關閉輸出：`startup.info.enabled: false`

## 相關

- 初學者：`docs/初學者學習說明書.md` §2
- 圖解：`docs/codeGraphic.html`「5 分鐘上手」
- 設計：`docs/superpowers/specs/2026-07-27-local-h2-startup-design.md`
