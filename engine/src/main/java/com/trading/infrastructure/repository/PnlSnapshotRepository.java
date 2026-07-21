package com.trading.infrastructure.repository;

import com.trading.infrastructure.entity.PnlSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * 【職責】損益快照持久化存取：依日期／標的查歷史日終 PnL。
 * 【技巧】Spring Data JPA 方法命名查詢。
 * 【概念】快照寫入由 JOB-B 完成；此介面供報表與回溯查詢，不含計算。
 * 【邊界】不含標記價取得或未實現損益公式。
 */
public interface PnlSnapshotRepository extends JpaRepository<PnlSnapshotEntity, Long> {

    /** 查指定日期的所有快照。 */
    List<PnlSnapshotEntity> findBySnapshotDate(LocalDate snapshotDate);

    /** 查指定日期與標的的快照。 */
    List<PnlSnapshotEntity> findBySnapshotDateAndSymbol(LocalDate snapshotDate, String symbol);
}
