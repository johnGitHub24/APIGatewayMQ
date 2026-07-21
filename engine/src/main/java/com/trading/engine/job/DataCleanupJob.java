package com.trading.engine.job;

import com.trading.application.DataCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 【職責】JOB-D 排程觸發器：定時清理過期審計事件與已終結失敗指令。
 * 【技巧】{@code @Scheduled(cron)}；{@code @ConditionalOnProperty} 可關閉；例外只記 log。
 * 【概念】Job 類只負責「何時跑」；「刪什麼、保留幾天」在 {@link DataCleanupService}。
 * 【邊界】不實作刪除 SQL；失敗不向上拋，避免中斷排程執行緒。
 */
@Component
@ConditionalOnProperty(name = "trading.job.cleanup.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DataCleanupJob {

    private final DataCleanupService dataCleanupService;

    /** 建構子注入 {@link DataCleanupService}。 */
    public DataCleanupJob(DataCleanupService dataCleanupService) {
        this.dataCleanupService = dataCleanupService;
    }

    /**
     * 【職責】cron 觸發後委派 {@link DataCleanupService#cleanup()}。
     * 【技巧】{@code try/catch} 吞掉例外並 {@code log.error}。
     * 【概念】排程執行緒若因未捕捉例外而死，後續觸發會停——故 Job 入口必須防禦性記錄。
     */
    @Scheduled(cron = "${trading.job.cleanup.cron}")
    public void run() {
        try {
            dataCleanupService.cleanup();
        } catch (Exception ex) {
            log.error("JOB-D data cleanup failed", ex);
        }
    }
}
