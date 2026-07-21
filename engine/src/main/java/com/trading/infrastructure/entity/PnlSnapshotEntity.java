package com.trading.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 【職責】映射 {@code pnl_snapshots}：日終持倉損益快照，供歷史查詢與報表。
 * 【技巧】JPA Entity；{@link LocalDate} 存交易日、{@link OffsetDateTime} 存寫入時間。
 * 【概念】JOB-B 把「當下 PnL」固化成列；之後查歷史不必重算整段成交與標記價。
 * 【邊界】不含快照計算邏輯；只存結果列。
 */
@Getter
@Setter
@Entity
@Table(name = "pnl_snapshots")
public class PnlSnapshotEntity {

    /** 主鍵（資料庫遞增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 快照交易日。 */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /** 交易標的。 */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 日終持倉數量。 */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity = BigDecimal.ZERO;

    /** 加權平均成本。 */
    @Column(name = "avg_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal avgPrice = BigDecimal.ZERO;

    /** 標記價格。 */
    @Column(name = "mark_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal markPrice = BigDecimal.ZERO;

    /** 未實現損益。 */
    @Column(name = "unrealized_pnl", nullable = false, precision = 18, scale = 8)
    private BigDecimal unrealizedPnl = BigDecimal.ZERO;

    /** 寫入時間。 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
