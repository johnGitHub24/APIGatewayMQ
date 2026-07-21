package com.trading.engine.job;

import com.trading.application.FailedCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 【職責】JOB-C 排程觸發器：定時重試失敗的下單指令（持久化 DLQ）。
 * 【技巧】{@code @Scheduled(cron)}；{@code @ConditionalOnProperty}；例外僅記錄。
 * 【概念】Consumer 寫入 failed_commands 後，由此 Job 依 backoff 再投——把「暫時故障」與「永久失敗」分開。
 * 【邊界】不決定重試策略細節（在 Service／設定）；不處理業務拒單語意。
 */
@Component
@ConditionalOnProperty(name = "trading.job.retry.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class FailedCommandRetryJob {

    private final FailedCommandService failedCommandService;

    /** 建構子注入 {@link FailedCommandService}。 */
    public FailedCommandRetryJob(FailedCommandService failedCommandService) {
        this.failedCommandService = failedCommandService;
    }

    /**
     * 【職責】cron 觸發後委派 {@link FailedCommandService#retryFailedCommands()}。
     * 【技巧】防禦性 {@code try/catch}，避免中斷排程執行緒。
     * 【概念】手動觸發見 {@link com.trading.api.JobController}；此處是自動化路徑。
     */
    @Scheduled(cron = "${trading.job.retry.cron}")
    public void run() {
        try {
            failedCommandService.retryFailedCommands();
        } catch (Exception ex) {
            log.error("JOB-C failed command retry failed", ex);
        }
    }
}
