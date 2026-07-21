package com.trading.infrastructure.repository;

import com.trading.domain.FailedCommandStatus;
import com.trading.infrastructure.entity.FailedCommandEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 【職責】失敗指令（DLQ）持久化存取：待重試查詢、狀態統計與過期清理。
 * 【技巧】Spring Data JPA 方法命名查詢 + {@code @Modifying}/{@code @Query} 批次刪除。
 * 【概念】Repository 只做查寫，不含「何時重試、如何重放」；那是 JOB-C／Service 的事。
 * 【邊界】不含商業規則與 HTTP。
 */
public interface FailedCommandRepository extends JpaRepository<FailedCommandEntity, Long> {

    /** 查到期且指定狀態的指令（重試排程用）。 */
    List<FailedCommandEntity> findByStatusAndNextRetryAtLessThanEqual(
            FailedCommandStatus status, OffsetDateTime now, Pageable pageable);

    /** 依狀態分頁查詢。 */
    List<FailedCommandEntity> findByStatus(FailedCommandStatus status, Pageable pageable);

    /** 統計指定狀態筆數。 */
    long countByStatus(FailedCommandStatus status);

    /** 刪除指定狀態且更新時間早於截止的舊紀錄（JOB-D 清理）。 */
    @Modifying
    @Query("DELETE FROM FailedCommandEntity f WHERE f.status IN :statuses AND f.updatedAt < :cutoff")
    int deleteByStatusInAndUpdatedAtBefore(
            @Param("statuses") Collection<FailedCommandStatus> statuses,
            @Param("cutoff") OffsetDateTime cutoff);
}
