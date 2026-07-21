package com.trading.infrastructure.repository;

import com.trading.infrastructure.entity.OrderEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 【職責】訂單事件持久化存取：依訂單查時間軸、統計與批次清理舊事件。
 * 【技巧】Spring Data JPA；清理用 {@code @Modifying} JPQL。
 * 【概念】事件表會隨交易成長；查詢與清理方法分開，避免在 Service 寫原生 SQL。
 * 【邊界】不含事件語意編排。
 */
public interface OrderEventRepository extends JpaRepository<OrderEventEntity, Long> {

    /** 依訂單 ID 查事件，建立時間升序。 */
    List<OrderEventEntity> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    /** 統計建立時間早於截止的事件筆數。 */
    long countByCreatedAtBefore(OffsetDateTime cutoff);

    /** 刪除建立時間早於截止的舊事件（JOB-D）。 */
    @Modifying
    @Query("DELETE FROM OrderEventEntity e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
