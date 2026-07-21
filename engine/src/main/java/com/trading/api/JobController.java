package com.trading.api;

import com.trading.application.DataCleanupService;
import com.trading.application.FailedCommandService;
import com.trading.application.PnlSnapshotService;
import com.trading.application.StaleOrderCancellationService;
import com.trading.domain.FailedCommandStatus;
import com.trading.dto.FailedCommandResponse;
import com.trading.dto.JobRunResponse;
import com.trading.dto.PnlSnapshotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 【職責】排程 Job 的手動觸發與輸出查詢 REST 入口（JOB-A～D），供監控／維運與 Swagger 文件使用。
 * 【技巧】{@code @RestController} + {@code @RequestMapping("/api/v1")}；{@code @Operation}/{@code @Tag} 產生 OpenAPI；
 *         委派對應 Service，以 {@link JobRunResponse#of} 組裝執行摘要。
 * 【概念】排程通常由 cron 自動跑；此 Controller 讓人「手動再跑一次」方便除錯與 Demo，
 *         本身不含清理／重試邏輯——那是 Service／Job 的事。
 * 【邊界】不實作 Job 商業邏輯、不直接操作 Repository；只轉交與組 HTTP 回應。
 */
@Tag(name = "Jobs", description = "排程 Job（JOB-A 超時取消 / JOB-B PnL 快照 / JOB-C 失敗重試 / JOB-D 資料清理）")
@RestController
@RequestMapping("/api/v1")
public class JobController {

    private final StaleOrderCancellationService staleOrderCancellationService;
    private final PnlSnapshotService pnlSnapshotService;
    private final FailedCommandService failedCommandService;
    private final DataCleanupService dataCleanupService;

    /** 建構子注入四個 Job 對應服務。 */
    public JobController(StaleOrderCancellationService staleOrderCancellationService,
                         PnlSnapshotService pnlSnapshotService,
                         FailedCommandService failedCommandService,
                         DataCleanupService dataCleanupService) {
        this.staleOrderCancellationService = staleOrderCancellationService;
        this.pnlSnapshotService = pnlSnapshotService;
        this.failedCommandService = failedCommandService;
        this.dataCleanupService = dataCleanupService;
    }

    /**
     * 【職責】手動執行 JOB-A：取消逾時未成交訂單。
     * 【技巧】{@code @PostMapping}；呼叫 {@link StaleOrderCancellationService#cancelStaleOrders()} 後包成 {@link JobRunResponse}。
     * 【概念】回傳「影響筆數」讓維運一眼知道這次跑了多少；真正取消條件在 Service。
     * @return 含 job 代碼、影響筆數與說明的執行摘要
     */
    @Operation(summary = "JOB-A 手動執行：取消逾時未成交訂單")
    @PostMapping("/jobs/stale-order-cancellation")
    public JobRunResponse runStaleOrderCancellation() {
        int cancelled = staleOrderCancellationService.cancelStaleOrders();
        return JobRunResponse.of("JOB-A", cancelled, "cancelled stale orders");
    }

    /**
     * 【職責】手動執行 JOB-B：建立當日 PnL／持倉結算快照。
     * 【技巧】委派 {@link PnlSnapshotService#captureSnapshot()}。
     * 【概念】快照是「當下持倉的凍結影像」，方便日後對帳；重跑應具冪等（同日同標的不重複寫）。
     * @return 本次寫入快照筆數摘要
     */
    @Operation(summary = "JOB-B 手動執行：建立當日 PnL/持倉結算快照")
    @PostMapping("/jobs/pnl-snapshot")
    public JobRunResponse runPnlSnapshot() {
        int written = pnlSnapshotService.captureSnapshot();
        return JobRunResponse.of("JOB-B", written, "pnl snapshots written");
    }

    /**
     * 【職責】手動執行 JOB-C：重試失敗的下單指令。
     * 【技巧】委派 {@link FailedCommandService#retryFailedCommands()}。
     * 【概念】基礎設施失敗會進 DLQ；此端點等同「現在立刻再試」，不必等下一個 cron。
     * @return 本次成功重投筆數摘要
     */
    @Operation(summary = "JOB-C 手動執行：重試失敗的下單指令")
    @PostMapping("/jobs/failed-command-retry")
    public JobRunResponse runFailedCommandRetry() {
        int succeeded = failedCommandService.retryFailedCommands();
        return JobRunResponse.of("JOB-C", succeeded, "failed commands replayed");
    }

    /**
     * 【職責】手動執行 JOB-D：清理過期審計事件與失敗指令。
     * 【技巧】讀取 {@link DataCleanupService.CleanupResult} record 組裝 detail 字串。
     * 【概念】保留期過了的事件／已終結指令可刪，避免表無限成長；刪除策略在 Service。
     * @return 刪除總筆數與分項說明
     */
    @Operation(summary = "JOB-D 手動執行：清理過期審計事件與失敗指令")
    @PostMapping("/jobs/cleanup")
    public JobRunResponse runCleanup() {
        DataCleanupService.CleanupResult result = dataCleanupService.cleanup();
        long total = (long) result.deletedOrderEvents() + result.deletedFailedCommands();
        String detail = "events=" + result.deletedOrderEvents() + ", failedCommands=" + result.deletedFailedCommands();
        return JobRunResponse.of("JOB-D", total, detail);
    }

    /**
     * 【職責】查詢 PnL 結算快照（JOB-B 輸出）。
     * 【技巧】{@code @DateTimeFormat(iso = DATE)} 解析可選日期；Stream {@code map} 轉 DTO。
     * 【概念】未指定日期＝今日，方便 Dashboard 預設看「今天結算」。
     * @param date 可選結算日（yyyy-MM-dd）；null 時用今日
     * @return 該日快照列表
     */
    @Operation(summary = "查詢 PnL 結算快照（JOB-B 輸出），未指定日期則為今日")
    @GetMapping("/pnl-snapshots")
    public List<PnlSnapshotResponse> listPnlSnapshots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return pnlSnapshotService.findByDate(date).stream()
                .map(s -> new PnlSnapshotResponse(s.getId(), s.getSnapshotDate(), s.getSymbol(),
                        s.getQuantity(), s.getAvgPrice(), s.getMarkPrice(), s.getUnrealizedPnl(), s.getCreatedAt()))
                .toList();
    }

    /**
     * 【職責】查詢失敗指令 DLQ（JOB-C 佇列），可依狀態過濾。
     * 【技巧】可選 {@link FailedCommandStatus} query param；Stream 映射為 {@link FailedCommandResponse}。
     * 【概念】PENDING／SUCCEEDED／DEAD 讓維運分辨「還會重試／已成功／已放棄」。
     * @param status 可選狀態過濾；null 表示不限
     * @return 失敗指令列表（有上限，見 Service）
     */
    @Operation(summary = "查詢失敗指令 DLQ（JOB-C 佇列），可依狀態過濾")
    @GetMapping("/failed-commands")
    public List<FailedCommandResponse> listFailedCommands(
            @RequestParam(required = false) FailedCommandStatus status) {
        return failedCommandService.findByStatus(status).stream()
                .map(c -> new FailedCommandResponse(c.getId(), c.getCommandId(), c.getClientOrderId(),
                        c.getSymbol(), c.getSide(), c.getQuantity(), c.getPrice(), c.getAttempts(),
                        c.getStatus(), c.getFailureReason(), c.getNextRetryAt()))
                .toList();
    }
}
