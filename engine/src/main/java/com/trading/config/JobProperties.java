package com.trading.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 【職責】綁定背景排程（JOB-A～D）的啟用、cron、批次大小與保留天數等設定。
 * 【技巧】{@code @ConfigurationProperties(prefix = "trading.job")} + 巢狀靜態類對應 YAML 階層。
 * 【概念】排程參數外置後，正式環境可用環境變數／設定檔覆寫，不必改程式重編。
 * 【邊界】只承載設定值；實際觸發與業務邏輯在 Job／Service。
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "trading.job")
public class JobProperties {

    /** JOB-A：逾時訂單自動取消。 */
    private StaleOrder staleOrder = new StaleOrder();
    /** JOB-B：每日 PnL 快照寫入。 */
    private PnlSnapshot pnlSnapshot = new PnlSnapshot();
    /** JOB-C：失敗命令自動重試。 */
    private Retry retry = new Retry();
    /** JOB-D：過期事件與死信紀錄清理。 */
    private Cleanup cleanup = new Cleanup();

    /**
     * 【職責】JOB-A 設定：掃描並取消長時間未成交的訂單。
     * 【技巧】巢狀 Properties 類，對應 {@code trading.job.stale-order.*}。
     * 【概念】逾時取消避免掛單無限占用風控額度；timeout／batchSize 決定掃描節奏與負載。
     */
    @Getter
    @Setter
    public static class StaleOrder {
        /** 是否啟用此排程。 */
        private boolean enabled = true;
        /** 訂單超過此秒數未成交即視為逾時。 */
        private long timeoutSeconds = 300;
        /** 每次排程最多處理的訂單筆數。 */
        private int batchSize = 200;
        /** Spring cron 表達式，預設每 5 分鐘執行一次。 */
        private String cron = "0 */5 * * * *";
    }

    /**
     * 【職責】JOB-B 設定：日終寫入各標的 PnL 快照。
     * 【技巧】巢狀 Properties，對應 {@code trading.job.pnl-snapshot.*}。
     * 【概念】快照把「當下持倉損益」固化成歷史點，報表不必重算整段成交。
     */
    @Getter
    @Setter
    public static class PnlSnapshot {
        /** 是否啟用此排程。 */
        private boolean enabled = true;
        /** Spring cron 表達式，預設每天 00:00 執行。 */
        private String cron = "0 0 0 * * *";
    }

    /**
     * 【職責】JOB-C 設定：自動重試 DLQ 中的失敗命令。
     * 【技巧】巢狀 Properties；maxAttempts／backoffSeconds 控制重試上限與退避。
     * 【概念】MQ 消費失敗不應立刻放棄；有限次重試 + DEAD 標記，兼顧自動恢復與人工介入。
     */
    @Getter
    @Setter
    public static class Retry {
        /** 是否啟用此排程。 */
        private boolean enabled = true;
        /** 單筆命令最多重試次數，超過後標記為 DEAD。 */
        private int maxAttempts = 3;
        /** 兩次重試之間的等待秒數（退避間隔）。 */
        private long backoffSeconds = 30;
        /** 每次排程最多重試的命令筆數。 */
        private int batchSize = 100;
        /** Spring cron 表達式，預設每分鐘執行一次。 */
        private String cron = "0 * * * * *";
    }

    /**
     * 【職責】JOB-D 設定：清理過期訂單事件與死信紀錄。
     * 【技巧】巢狀 Properties；retentionDays + batchSize 控制刪除範圍與單次負載。
     * 【概念】事件／DLQ 會持續成長；定期清理避免表膨脹，同時保留足夠稽核窗口。
     */
    @Getter
    @Setter
    public static class Cleanup {
        /** 是否啟用此排程。 */
        private boolean enabled = true;
        /** 訂單事件保留天數，超過即刪除。 */
        private int eventRetentionDays = 30;
        /** 失敗命令（DLQ）保留天數，超過即刪除。 */
        private int failedCommandRetentionDays = 7;
        /** 每次排程最多刪除的筆數。 */
        private int batchSize = 500;
        /** Spring cron 表達式，預設每天 00:30 執行。 */
        private String cron = "0 30 0 * * *";
    }
}
