package com.trading.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】成交明細查詢回應（完整版）：含 tradeId／orderId，供成交列表 API。
 * 【技巧】Lombok {@code @Data}；比 {@link TradeResponse} 多識別欄位。
 * 【概念】嵌入訂單回應時用精簡 {@link TradeResponse}；獨立查成交列表需要可追蹤的 ID。
 */
@Data
public class TradeDetailResponse {

    /** 成交主鍵。 */
    private Long tradeId;
    /** 所屬訂單主鍵。 */
    private Long orderId;
    /** 實際成交價格。 */
    private BigDecimal executedPrice;
    /** 實際成交數量。 */
    private BigDecimal executedQty;
    /** 成交時間。 */
    private OffsetDateTime executedAt;
}
