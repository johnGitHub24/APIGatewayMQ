package com.trading.dto;

import com.trading.domain.FailedCommandStatus;
import com.trading.domain.OrderSide;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】失敗命令（DLQ）查詢的回應模型：保留原始下單內容、重試次數與失敗原因。
 * 【技巧】Java {@code record} 作為不可變 API 回應；欄位語意見元件名稱。
 * 【概念】削峰架構下消費可能失敗；把指令落地到 DLQ 後，營運可查詢／手動重試，而不靠 Kafka 日誌肉眼找。
 * 【邊界】只描述查詢結果；重試觸發由 Job／管理 API 負責。
 *
 * @param id            失敗紀錄主鍵
 * @param commandId     MQ 訊息唯一指令 ID
 * @param clientOrderId 客戶端冪等識別碼
 * @param symbol        原始標的
 * @param side          原始買賣方向
 * @param quantity      原始委託數量
 * @param price         原始委託價格
 * @param attempts      已重試次數
 * @param status        PENDING／SUCCEEDED／DEAD
 * @param failureReason 最後失敗原因
 * @param nextRetryAt   下次自動重試時間；DEAD 時可為 null
 */
public record FailedCommandResponse(
        Long id,
        String commandId,
        String clientOrderId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        int attempts,
        FailedCommandStatus status,
        String failureReason,
        OffsetDateTime nextRetryAt) {
}
