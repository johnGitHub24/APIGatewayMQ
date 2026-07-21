package com.trading.dto;

import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】訂單查詢／下單操作的 HTTP 回應：基本欄位、成交進度、風控結果與時間戳。
 * 【技巧】Lombok {@code @Data} POJO；可嵌 {@link TradeResponse} 列表供查單展開成交。
 * 【概念】回應 DTO 是對外契約：隱藏 Entity 細節（如 JPA 註解），只暴露客戶端需要的欄位。
 * 【邊界】不負責組裝邏輯；由 Mapper／Service 填值。
 */
@Data
public class OrderResponse {

    /** 系統訂單主鍵。 */
    private Long orderId;
    /** 客戶端冪等識別碼（若有）。 */
    private String clientOrderId;
    /** 交易標的。 */
    private String symbol;
    /** 買賣方向。 */
    private OrderSide side;
    /** 原始委託數量。 */
    private BigDecimal quantity;
    /** 已成交數量。 */
    private BigDecimal filledQuantity;
    /** 委託價格。 */
    private BigDecimal price;
    /** 目前狀態。 */
    private OrderStatus status;
    /** 風控拒絕原因（若有）。 */
    private String rejectReason;
    /** 觸發拒絕的風控規則代碼（若有）。 */
    private String riskRuleCode;
    /** 最近一筆成交摘要（向下相容）。 */
    private TradeResponse trade;
    /** 此訂單全部成交列表。 */
    private java.util.List<TradeResponse> trades;
    /** 建立時間。 */
    private OffsetDateTime createdAt;
    /** 最後更新時間。 */
    private OffsetDateTime updatedAt;
}
