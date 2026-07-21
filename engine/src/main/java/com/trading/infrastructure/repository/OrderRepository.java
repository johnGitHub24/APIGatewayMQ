package com.trading.infrastructure.repository;

import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import com.trading.infrastructure.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 【職責】訂單持久化存取：冪等鍵查詢、條件分頁、逾時掃描與內容查重。
 * 【技巧】Spring Data JPA 方法命名推導查詢；{@link Pageable} 做分頁保護。
 * 【概念】風控重複單、JOB-A 逾時取消都依賴這些查詢；Repository 只回資料，判斷在 Service。
 * 【邊界】不含狀態轉移或風控規則。
 */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    /** 依客戶端冪等鍵查單筆。 */
    Optional<OrderEntity> findByClientOrderId(String clientOrderId);

    /** 依標的分頁查詢。 */
    Page<OrderEntity> findBySymbol(String symbol, Pageable pageable);

    /** 依狀態分頁查詢。 */
    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);

    /** 依標的＋狀態分頁查詢。 */
    Page<OrderEntity> findBySymbolAndStatus(String symbol, OrderStatus status, Pageable pageable);

    /** 查指定狀態且建立時間早於截止的訂單（逾時掃描）。 */
    List<OrderEntity> findByStatusInAndCreatedAtBefore(
            Collection<OrderStatus> statuses, OffsetDateTime cutoff, Pageable pageable);

    /** 統計指定時間之後建立的訂單筆數。 */
    long countByCreatedAtAfter(OffsetDateTime since);

    /** 內容查重：同標的／方向／量／價且在時窗內是否已存在。 */
    boolean existsBySymbolAndSideAndQuantityAndPriceAndCreatedAtAfter(
            String symbol, OrderSide side, BigDecimal quantity, BigDecimal price, OffsetDateTime since);
}
