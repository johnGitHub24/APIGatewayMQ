package com.trading.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 【職責】Gateway → Engine 的 Kafka 下單指令訊息（跨服務契約）。
 * 【技巧】Lombok Builder 組裝；金額用 {@link BigDecimal}；時間用 {@link Instant}（需搭配 JavaTimeModule 序列化）。
 * 【概念】削峰的「信封」：Gateway 快速寫入 {@link Topics#ORDER_COMMANDS}，Engine Consumer 依此結構反序列化後處理。
 *         兩邊共用同一類別，避免各自定義 DTO 造成契約漂移。
 * 【邊界】只描述命令內容；不含受理 HTTP 回應（見 {@link OrderAcceptedResponse}）、不含成交結果事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCommandMessage {

    /** 指令唯一 ID（端到端追蹤）。 */
    private String commandId;
    /** 客戶端訂單 ID（冪等鍵）。 */
    private String clientOrderId;
    /** 交易標的，例如 BTCUSDT。 */
    private String symbol;
    /** 買賣方向（BUY／SELL 字串）。 */
    private String side;
    /** 委託數量。 */
    private BigDecimal quantity;
    /** 委託價格。 */
    private BigDecimal price;
    /** Gateway 送出時間。 */
    private Instant submittedAt;
    /** 來源 Gateway 節點名稱（多副本時方便追蹤）。 */
    private String sourceGateway;
}
