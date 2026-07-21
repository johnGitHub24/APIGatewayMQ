package com.trading.infrastructure.entity;

import com.trading.domain.OrderEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 【職責】映射 {@code order_events}：記錄訂單生命週期中的每個事件（稽核時間軸）。
 * 【技巧】JPA Entity；以 {@code order_id} 關聯訂單；{@link OrderEventType} 以 STRING 存。
 * 【概念】狀態是當下快照，事件是過程軌跡；兩者並存才能回答「為何變成 REJECTED」。
 * 【邊界】不負責事件語意編排；由應用層在狀態變更時寫入。
 */
@Getter
@Setter
@Entity
@Table(name = "order_events")
public class OrderEventEntity {

    /** 主鍵（資料庫遞增）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 關聯訂單主鍵。 */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 事件類型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderEventType event;

    /** 風控規則代碼（若有）。 */
    @Column(name = "risk_rule_code", length = 16)
    private String riskRuleCode;

    /** 拒絕原因（若有）。 */
    @Column(name = "reject_reason")
    private String rejectReason;

    /** 事件附加 JSON 載荷。 */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /** 事件發生時間。 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
