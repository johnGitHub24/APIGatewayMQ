# APIGatewayMQ — 專案規則（薄）

繼承：EngineeringOS eos-minimal @ 0.1.5  
公版路徑：`d:\ClaudeCode\EngineeringOS\eos-minimal\`  
權威規格：[APIGatewayMQ 規格書.md](APIGatewayMQ%20規格書.md)

## 與公版差異

- 多模組：`common` + `gateway`（WebFlux :8080）+ `engine`（:8081／多副本）
- 基礎設施：Kafka、Redis 限流、PostgreSQL（Docker）／H2（test）
- 驗證入口：`.\scripts\check.ps1`（`gradlew check`，unit + integration）
- Docs standard：`knowledge/documentation.md`

## 本專案專屬

- Domain：非同步下單削峰、Consumer Group、冪等、風控／狀態機（Engine）
- 架構：`docs/architecture.md`；DB：`docs/資料庫設計.md`；驗證：`docs/驗證設計.md`
- 測試：`docs/testing.md`、`docs/測試與CI.md`
- API 契約：[API規格書.md](API規格書.md)

## 註解深度
- comment_verbosity: **detailed**
- 權威：`EngineeringOS/eos-minimal/knowledge/comments.md` §0／§3b（eos-minimal @ 0.1.5）
- 結構：【職責】【技巧】【概念】；簡單 getter 可併入類別說明


## Git Remote
- 帳號：`johnGitHub24`；一專案一 repo
- 規範：`EngineeringOS/eos-minimal/knowledge/專案上船-GitHub.md`

## 回寫

問題與公版改善建議 → `EngineeringOS/eos-minimal/feedback/SYNC_LOG.md`
