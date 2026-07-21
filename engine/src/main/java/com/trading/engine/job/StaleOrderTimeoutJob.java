package com.trading.engine.job;

import com.trading.application.StaleOrderCancellationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 【職責】JOB-A 排程觸發器：定時取消逾時未成交訂單。
 * 【技巧】{@code @Scheduled(cron)}；{@code @ConditionalOnProperty}；例外僅記錄。
 * 【概念】掛單太久未成交會佔用風險額度；逾時自動取消是常見風控／營運手段。
 * 【邊界】逾時秒數與可取消狀態在 Service／設定；本類只負責觸發。
 */
@Component
@ConditionalOnProperty(name = "trading.job.stale-order.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class StaleOrderTimeoutJob {

    private final StaleOrderCancellationService staleOrderCancellationService;

    /** 建構子注入 {@link StaleOrderCancellationService}。 */
    public StaleOrderTimeoutJob(StaleOrderCancellationService staleOrderCancellationService) {
        this.staleOrderCancellationService = staleOrderCancellationService;
    }

    /**
     * 【職責】cron 觸發後委派 {@link StaleOrderCancellationService#cancelStaleOrders()}。
     * 【技巧】防禦性 {@code try/catch}。
     * 【概念】與手動 {@code POST /jobs/stale-order-cancellation} 共用同一 Service，行為一致。
     */
    @Scheduled(cron = "${trading.job.stale-order.cron}")
    public void run() {
        try {
            staleOrderCancellationService.cancelStaleOrders();
        } catch (Exception ex) {
            log.error("JOB-A stale order cancellation failed", ex);
        }
    }
}
