package com.trading.infrastructure.repository;

import com.trading.infrastructure.entity.TradeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 【職責】成交紀錄持久化存取：依訂單 ID 查關聯成交（列表或分頁）。
 * 【技巧】Spring Data JPA；同名方法以回傳型別區分 List／Page。
 * 【概念】查單展開成交用 List；成交列表 API 用 Page 做分頁保護。
 * 【邊界】不含撮合或持倉更新。
 */
public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    /** 依訂單 ID 查全部成交（不分頁）。 */
    List<TradeEntity> findByOrderId(Long orderId);

    /** 依訂單 ID 分頁查成交。 */
    Page<TradeEntity> findByOrderId(Long orderId, Pageable pageable);
}
