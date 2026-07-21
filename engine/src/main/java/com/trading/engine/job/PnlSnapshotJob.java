package com.trading.engine.job;

import com.trading.application.PnlSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 【職責】JOB-B 排程觸發器：每日建立 PnL／持倉結算快照。
 * 【技巧】{@code @Scheduled(cron)}；{@code @ConditionalOnProperty}；例外僅記錄。
 * 【概念】日終（或指定 cron）把當下持倉「拍一張照片」存檔，方便歷史對帳與報表。
 * 【邊界】不計算 PnL 公式、不寫快照列（在 {@link PnlSnapshotService}）。
 */
@Component
@ConditionalOnProperty(name = "trading.job.pnl-snapshot.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class PnlSnapshotJob {

    private final PnlSnapshotService pnlSnapshotService;

    /** 建構子注入 {@link PnlSnapshotService}。 */
    public PnlSnapshotJob(PnlSnapshotService pnlSnapshotService) {
        this.pnlSnapshotService = pnlSnapshotService;
    }

    /**
     * 【職責】cron 觸發後委派 {@link PnlSnapshotService#captureSnapshot()}。
     * 【技巧】防禦性 {@code try/catch}。
     * 【概念】重跑應冪等：同日同標的已有快照則跳過，避免重複列。
     */
    @Scheduled(cron = "${trading.job.pnl-snapshot.cron}")
    public void run() {
        try {
            pnlSnapshotService.captureSnapshot();
        } catch (Exception ex) {
            log.error("JOB-B PnL snapshot failed", ex);
        }
    }
}
