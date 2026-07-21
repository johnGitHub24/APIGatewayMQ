package com.trading.infrastructure.entity;

import com.trading.domain.FailedCommandStatus;
import com.trading.domain.OrderSide;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】映射 {@code failed_commands}：Kafka 下單指令失敗後的持久化重試佇列。
 * 【技巧】JPA {@code @Entity}；{@code @Enumerated(STRING)} 存狀態／方向；Hibernate 時間戳註解。
 * 【概念】削峰消費失敗時不能只丟 log；落地原始指令 + attempts／nextRetryAt，JOB-C 才能自動重試。
 * 【邊界】不含重試業務邏輯；只存欄位。狀態語意見 {@link FailedCommandStatus}。
 */
@Getter
@Setter
@Entity
@Table(name = "failed_commands")
public class FailedCommandEntity {

    /** 主鍵（資料庫遞增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kafka 訊息唯一指令 ID。 */
    @Column(name = "command_id", length = 64)
    private String commandId;

    /** 客戶端冪等鍵。 */
    @Column(name = "client_order_id", length = 64)
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

    /** 失敗原因。 */
    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    /** 已重試次數。 */
    @Column(nullable = false)
    private int attempts = 0;

    /** 重試狀態（PENDING／SUCCEEDED／DEAD）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FailedCommandStatus status = FailedCommandStatus.PENDING;

    /** 下次重試時間。 */
    @Column(name = "next_retry_at", nullable = false)
    private OffsetDateTime nextRetryAt;

    /** 建立時間。 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 最後更新時間。 */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
