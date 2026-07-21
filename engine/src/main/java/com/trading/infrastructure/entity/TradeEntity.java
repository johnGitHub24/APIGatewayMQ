package com.trading.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】映射 {@code trades}：單筆（部分或全部）成交紀錄。
 * 【技巧】JPA Entity；以 {@code order_id} 關聯訂單；成交時間用 {@code @CreationTimestamp}。
 * 【概念】一筆訂單可對多筆成交（部分成交）；成交是持倉與 filledQuantity 更新的事實來源。
 * 【邊界】不含撮合引擎；本專案成交由應用流程寫入。
 */
@Getter
@Setter
@Entity
@Table(name = "trades")
public class TradeEntity {

    /** 主鍵（資料庫遞增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 關聯訂單主鍵。 */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 實際成交價格。 */
    @Column(name = "executed_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal executedPrice;

    /** 實際成交數量。 */
    @Column(name = "executed_qty", nullable = false, precision = 18, scale = 8)
    private BigDecimal executedQty;

    /** 成交時間。 */
    @CreationTimestamp
    @Column(name = "executed_at", nullable = false, updatable = false)
    private OffsetDateTime executedAt;
}
