package com.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】成交摘要回應（精簡版）：嵌入 {@link OrderResponse} 描述單筆成交。
 * 【技巧】Lombok {@code @Data} + 全參／無參建構子，方便 Mapper 與測試組裝。
 * 【概念】同一領域「成交」依使用場景拆兩個 DTO：精簡嵌套 vs 完整列表，避免過度傳輸。
 * 【邊界】不含 tradeId／orderId；完整資訊請用 {@link TradeDetailResponse}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeResponse {

    /** 實際成交價格。 */
    private BigDecimal executedPrice;
    /** 實際成交數量。 */
    private BigDecimal executedQty;
    /** 成交時間。 */
    private OffsetDateTime executedAt;
}
