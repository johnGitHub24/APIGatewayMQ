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

框線內含：health、Swagger、（local）H2 Console、主要 API 路徑、學習文件提示。

實作：

- `engine/.../config/StartupInfoLogger.java`
- `gateway/.../config/StartupInfoLogger.java`

關閉輸出：`startup.info.enabled: false`

## 相關

- 初學者：`docs/初學者學習說明書.md` §2
- 圖解：`docs/codeGraphic.html`「5 分鐘上手」
- 設計：`docs/superpowers/specs/2026-07-27-local-h2-startup-design.md`
