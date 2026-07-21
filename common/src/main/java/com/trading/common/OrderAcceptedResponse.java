package com.trading.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 【職責】Gateway 受理下單後立即回傳的「已入隊」回應模型（通常搭配 HTTP 202）。
 * 【技巧】Lombok {@code @Data}／{@code @Builder}／無參與全參建構子，方便 JSON 序列化與測試組裝。
 * 【概念】這不是最終成交結果，而是非同步削峰流程的受理通知：客戶端先拿到 {@code commandId}／{@code pollUrl}，
 *         再另行查詢 Engine 處理進度。與同步「下單即回成交」的 API 語意不同。
 * 【邊界】僅 HTTP 回應契約；不含 Kafka 訊息欄位（見 {@link OrderCommandMessage}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAcceptedResponse {

    /** MQ 指令追蹤 ID（對應 Kafka commandId）。 */
    private String commandId;
    /** 客戶端冪等識別（可來自 Idempotency-Key）。 */
    private String clientOrderId;
    /** 受理狀態，預期通常為 ACCEPTED。 */
    private String status;
    /** 給呼叫端的人類可讀訊息。 */
    private String message;
    /** 建議輪詢 URL，供追蹤後續處理狀態。 */
    private String pollUrl;
    /** 系統受理時間。 */
    private Instant acceptedAt;
}
