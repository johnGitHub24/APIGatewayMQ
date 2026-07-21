package com.trading.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】映射 {@code positions}：每標的一筆淨持倉（數量、成本、未實現損益、標記價）。
 * 【技巧】JPA Entity；{@code symbol} unique；{@code @UpdateTimestamp} 維護更新時間。
 * 【概念】持倉是成交累積的結果狀態；下單風控會讀 quantity／曝險，成交後再回寫。
 * 【邊界】不含持倉演算法；由應用層依成交更新欄位。
 */
@Getter
@Setter
@Entity
@Table(name = "positions")
public class PositionEntity {

    /** 主鍵（資料庫遞增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 標的代碼（全表唯一）。 */
    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    /** 淨持倉（正多／負空）。 */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity = BigDecimal.ZERO;

    /** 加權平均成本。 */
    @Column(name = "avg_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal avgPrice = BigDecimal.ZERO;

    /** 未實現損益。 */
    @Column(name = "unrealized_pnl", nullable = false, precision = 18, scale = 8)
    private BigDecimal unrealizedPnl = BigDecimal.ZERO;

    /** 最新標記價格。 */
    @Column(name = "mark_price", precision = 18, scale = 8)
    private BigDecimal markPrice = BigDecimal.ZERO;

    /** 最後更新時間。 */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
