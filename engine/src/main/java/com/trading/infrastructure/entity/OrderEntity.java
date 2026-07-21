package com.trading.infrastructure.entity;

import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】映射 {@code orders}：儲存下單核心欄位、成交進度、狀態與風控拒絕資訊。
 * 【技巧】JPA Entity；{@code client_order_id} unique 支援冪等；enum 以 STRING 持久化。
 * 【概念】訂單是交易主檔；成交／事件／持倉都圍繞 orderId 關聯。Entity 只反映「存什麼」，狀態轉移在 Service。
 * 【邊界】不含風控規則執行或 Kafka 消費編排。
 */
@Getter
@Setter
@Entity
@Table(name = "orders")
public class OrderEntity {

    /** 主鍵（資料庫遞增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客戶端冪等鍵（唯一）。 */
    @Column(name = "client_order_id", unique = true, length = 64)
    private String clientOrderId;

    /** 交易標的。 */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 買賣方向。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private OrderSide side;

    /** 委託數量。 */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    /** 委託價格。 */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    /** 已成交數量。 */
    @Column(name = "filled_quantity", precision = 18, scale = 8)
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    /** 訂單狀態。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** 拒絕原因（若有）。 */
    @Column(name = "reject_reason")
    private String rejectReason;

    /** 觸發拒絕的風控規則代碼（若有）。 */
    @Column(name = "risk_rule_code", length = 16)
    private String riskRuleCode;

    /** 建立時間。 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 最後更新時間。 */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
