package com.trading.domain;

/**
 * 【職責】表達訂單生命週期狀態（領域枚舉）。
 * 【技巧】Java {@code enum}：型別安全、可當 switch／JPA 字串映射。
 * 【概念】合法路徑大致為 NEW → PARTIALLY_FILLED／FILLED／CANCELLED，或風控直接 REJECTED。
 *         用 enum 而非字串常數，編譯期就能擋掉拼錯狀態；狀態轉移規則仍應在 Service／狀態機檢查。
 */
public enum OrderStatus {
    /** 訂單已建立，尚未有任何成交。 */
    NEW,
    /** 訂單已部分成交，尚有未成交剩餘數量。 */
    PARTIALLY_FILLED,
    /** 訂單全部成交，生命週期正常結束。 */
    FILLED,
    /** 被風控或業務邏輯拒絕，不進入撮合。 */
    REJECTED,
    /** 被使用者或系統取消，未成交部分不再撮合。 */
    CANCELLED
}
