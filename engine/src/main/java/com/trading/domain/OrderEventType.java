package com.trading.domain;

/**
 * 【職責】定義訂單時間軸上可出現的事件類型（稽核／可觀測性）。
 * 【技巧】{@code enum} 作為事件分類標籤，寫入 {@code order_events} 後可供查詢與重播理解。
 * 【概念】狀態（{@link OrderStatus}）是「現在是什麼」；事件類型是「發生過什麼」。
 *         分開建模才能還原「為何變成 REJECTED」（例如 RISK_CHECK → REJECTED）而不只看到終態。
 */
public enum OrderEventType {
    /** 收到訂單請求。 */
    RECEIVED,
    /** 進行風控評估。 */
    RISK_CHECK,
    /** 風控通過。 */
    APPROVED,
    /** 風控拒絕。 */
    REJECTED,
    /** 部分成交。 */
    PARTIALLY_FILLED,
    /** 全部成交。 */
    FILLED,
    /** 已取消。 */
    CANCELLED,
    /** 結果記錄完成。 */
    OUTCOME_RECORDED,
    /** 觸發紀律標記。 */
    DISCIPLINE_FLAG,
    /** 持倉已更新。 */
    POSITION_UPDATED
}
