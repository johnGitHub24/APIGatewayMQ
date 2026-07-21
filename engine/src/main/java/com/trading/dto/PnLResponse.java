package com.trading.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 【職責】損益（PnL）摘要回應：總未實現損益 + 各標的分項，供 {@code GET /api/v1/pnl}。
 * 【技巧】外層彙總 + 巢狀 {@link PositionPnlItem}；Lombok {@code @Data}。
 * 【概念】即時 PnL 是「標記價格相對成本」的投影，與日終 {@link PnlSnapshotResponse} 快照用途不同。
 */
@Data
public class PnLResponse {

    /** 全部持倉加總的未實現損益。 */
    private BigDecimal totalUnrealizedPnl;
    /** 各標的損益明細。 */
    private List<PositionPnlItem> positions;
    /** 統計計算時間點。 */
    private OffsetDateTime asOf;

    /**
     * 【職責】單一標的的未實現損益項目。
     * 【技巧】靜態巢狀 DTO，序列化為 positions 陣列元素。
     * 【概念】總額與分項並存，方便儀表板「一眼總覽」與「下鑽標的」。
     */
    @Data
    public static class PositionPnlItem {
        /** 標的代碼。 */
        private String symbol;
        /** 該標的未實現損益。 */
        private BigDecimal unrealizedPnl;
    }
}
