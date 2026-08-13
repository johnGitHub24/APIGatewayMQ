# APIGatewayMQ — local H2 + StartupInfo + CodeGraphic（設計）

日期：2026-07-27

## 目標

讓初學者用 IntelliJ 直接跑 `TradingEngineApplication` **不必先開 PostgreSQL／Kafka**，先學會 Engine REST（下單／持倉／PnL）；並在 Console 印常用連結、補強 `codeGraphic.html` 學習路徑。

## 決策

| 項目 | 選擇 |
|------|------|
| 本機 DB | H2 in-memory（`MODE=PostgreSQL`） |
| Profile | `local` 為 **spring.profiles.default**；Docker 設 `SPRING_PROFILES_ACTIVE=docker` |
| Kafka | `local` 下 `spring.kafka.listener.auto-startup=false`（仍可走同步 REST） |
| StartupInfoLogger | gateway + engine 各一份，讀 `startup.info.*` |
| codeGraphic | 加「5 分鐘上手」分頁，說明 local vs Docker |

## 不做

- 不改業務／風控語意
- 不把 H2 當 Docker 全棧預設
- 不強制 Gateway 也改 H2（Gateway 無 JPA）

## 驗收

1. IntelliJ 無 Active profiles（或 `local`）啟動 Engine → 成功 → Console 有連結框
2. `docker compose` Engine 容器仍連 PostgreSQL
3. `docs/architecture.md` 先寫「IntelliJ + H2」再寫 Docker 全棧
