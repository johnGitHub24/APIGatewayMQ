package com.trading.domain;

/**
 * 【職責】表達失敗命令（DLQ）的處理狀態，追蹤重試進度。
 * 【技巧】領域 {@code enum}，與 {@code failed_commands.status} 以字串映射。
 * 【概念】MQ 消費失敗後進入死信佇列：PENDING 待重試 → SUCCEEDED 成功，或耗盡次數變 DEAD 需人工介入。
 *         狀態與「原始下單內容」分開存，才能在不丟訊息的前提下做有限次恢復。
 */
public enum FailedCommandStatus {
    /** 等待下次自動重試，尚未達最大重試次數。 */
    PENDING,
    /** 重試已成功，指令已正常處理完畢。 */
    SUCCEEDED,
    /** 已達最大重試次數，停止重試並標記為死信，需人工介入。 */
    DEAD
}
