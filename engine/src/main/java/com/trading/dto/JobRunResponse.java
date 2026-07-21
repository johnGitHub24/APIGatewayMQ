package com.trading.dto;

import java.time.OffsetDateTime;

/**
 * 【職責】手動觸發 Job 後回傳的執行結果摘要。
 * 【技巧】{@code record} + 靜態工廠 {@link #of} 自動填入執行時間。
 * 【概念】管理端需要知道「跑了哪個 Job、影響幾筆」；用統一回應避免各 Job 自訂 JSON 形狀。
 *
 * @param job        Job 代碼（JOB-A／B／C／D）
 * @param affected   受影響筆數
 * @param detail     補充說明
 * @param executedAt 執行時間
 */
public record JobRunResponse(String job, long affected, String detail, OffsetDateTime executedAt) {

    /**
     * 【職責】以當前時間戳建立 Job 回應。
     * 【技巧】靜態工廠隱藏 {@link OffsetDateTime#now()}。
     * 【概念】呼叫端不必每次手動塞時間，減少漏填。
     */
    public static JobRunResponse of(String job, long affected, String detail) {
        return new JobRunResponse(job, affected, detail, OffsetDateTime.now());
    }
}
