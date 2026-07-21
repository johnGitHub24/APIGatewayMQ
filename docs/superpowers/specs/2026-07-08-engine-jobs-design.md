# Engine 排程 Job 功能設計（JOB-A/B/C/D）

> 專案：APIGatewayMQ ‧ 模組：`engine` ‧ 日期：2026-07-08
> 依據：`APIGatewayMQ 規格書.md`、`APIGatewayMQ 架構（Spring Boot）.md`、`參考資料-houseHub測試方法論.md`

## 1. 目標

在 Engine 模組新增四個獨立排程 Job，每個功能各自一個 Job，並附完整單元測試與整合測試。

## 2. 架構原則

- 觸發層（thin）：`com.trading.engine.job` 內的 `@Scheduled` 觸發器，只負責排程與委派。
- 商業層：`com.trading.application` 內的 Service 承載邏輯，可獨立測試。
- 持久層：沿用 `infrastructure.entity` / `infrastructure.repository` 慣例。
- 設定：`JobProperties`（前綴 `trading.job`）集中管理 enable 開關、cron、門檻、批次上限。
- 排程開啟：`SchedulingConfig` 標註 `@EnableScheduling`。
- 觸發器以 `@ConditionalOnProperty(enabled, matchIfMissing=true)` 控制；測試環境關閉，避免干擾。
- 所有掃描查詢均帶批次上限（符合 CLAUDE.md「查詢必分頁」紅線）。

## 3. 四個 Job

### JOB-A 訂單超時自動取消
- 觸發：`StaleOrderTimeoutJob`；服務：`StaleOrderCancellationService`。
- 邏輯：掃描 `status IN (NEW, PARTIALLY_FILLED)` 且 `created_at < now - timeoutSeconds`，逐筆改為 `CANCELLED` 並寫 `OrderEventType.CANCELLED` 事件。
- Repository：`findByStatusInAndCreatedAtBefore(statuses, cutoff, pageable)`。
- 設定：`trading.job.stale-order.{enabled,timeout-seconds,batch-size,cron}`。

### JOB-B 每日持倉/PnL 結算快照
- 觸發：`PnlSnapshotJob`；服務：`PnlSnapshotService`。
- 邏輯：讀取所有 `positions`，為每個 symbol 寫一列快照到新表 `pnl_snapshots`。
- 新實體：`PnlSnapshotEntity`；Repository：`PnlSnapshotRepository`。
- 設定：`trading.job.pnl-snapshot.{enabled,cron}`。

### JOB-C 失敗訊息重試（DLQ）
- 觸發：`FailedCommandRetryJob`；服務：`FailedCommandService`。
- 邏輯：`OrderCommandConsumer` 遇非業務例外時將指令寫入 `failed_commands`（狀態 PENDING）。Job 掃描 `status=PENDING` 且 `nextRetryAt <= now` 且 `attempts < maxAttempts`，重新呼叫 `TradingService.placeOrder`；成功→SUCCEEDED，失敗→attempts+1、退避設定 nextRetryAt，超過上限→DEAD。
- 新實體：`FailedCommandEntity` + `FailedCommandStatus`；Repository：`FailedCommandRepository`。
- 設定：`trading.job.retry.{enabled,max-attempts,backoff-seconds,batch-size,cron}`。

### JOB-D 過期資料清理
- 觸發：`DataCleanupJob`；服務：`DataCleanupService`。
- 邏輯：批次刪除 `order_events` 早於 `event-retention-days` 的紀錄；刪除終態（SUCCEEDED/DEAD）且早於 `failed-command-retention-days` 的 `failed_commands`。
- Repository：`@Modifying` bulk delete + count。
- 設定：`trading.job.cleanup.{enabled,event-retention-days,failed-command-retention-days,cron}`。

## 4. 測試策略

| 層 | 工具 | 範圍 |
|----|------|------|
| 單元 | JUnit5 + Mockito | 各 Service 邏輯（mock repository） |
| 整合 | `@Tag("integration")` + `@SpringBootTest` + H2 | 各 Job 全流程走真 repository |

Case ID：`JOB-STALE-00x`、`JOB-PNL-00x`、`JOB-RETRY-00x`、`JOB-CLEAN-00x`。

## 5. DoD
- `gradlew :engine:test` 與 `:engine:integrationTest` 全綠。
- `schema.sql` 補上 `pnl_snapshots`、`failed_commands`。
- 主規格書第 6 章 Case ID 對照補上 JOB-* 列。
