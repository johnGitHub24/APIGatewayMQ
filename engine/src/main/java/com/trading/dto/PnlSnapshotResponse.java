package com.trading.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 【職責】PnL 日終快照回應（由 JOB-B 產生）：持倉、成本、標記價與未實現損益。
 * 【技巧】Java {@code record} 不可變回應；日期用 {@link LocalDate}、寫入時間用 {@link OffsetDateTime}。
 * 【概念】快照把當日結束狀態固化，歷史報表不必重算整段成交；與即時 {@link PnLResponse} 互補。
 *
 * @param id            快照主鍵
 * @param snapshotDate  快照交易日
 * @param symbol        標的代碼
 * @param quantity      日終持倉數量
 * @param avgPrice      加權平均成本
 * @param markPrice     結算標記價格
 * @param unrealizedPnl 未實現損益
 * @param createdAt     寫入時間
 */
public record PnlSnapshotResponse(
        Long id,
        LocalDate snapshotDate,
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal markPrice,
        BigDecimal unrealizedPnl,
        OffsetDateTime createdAt) {
}
