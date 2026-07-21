package com.trading.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】持倉查詢回應：標的淨部位、平均成本與未實現損益。
 * 【技巧】Lombok {@code @Data} POJO，對應 {@code GET /api/v1/positions} 等端點。
 * 【概念】正數 quantity 為多頭、負數為空頭；avgPrice 是計算未實現損益的成本基準。
 * 【邊界】不負責持倉更新；成交後由應用層改寫 Entity 再投影至此。
 */
@Data
public class PositionResponse {

    /** 標的代碼。 */
    private String symbol;
    /** 淨持倉數量（正多／負空）。 */
    private BigDecimal quantity;
    /** 加權平均持倉成本。 */
    private BigDecimal avgPrice;
    /** 未實現損益。 */
    private BigDecimal unrealizedPnl;
    /** 最後更新時間。 */
    private OffsetDateTime updatedAt;
}
