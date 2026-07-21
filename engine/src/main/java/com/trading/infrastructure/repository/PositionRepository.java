package com.trading.infrastructure.repository;

import com.trading.infrastructure.entity.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 【職責】持倉持久化存取：依標的查／更新淨部位。
 * 【技巧】Spring Data JPA；{@code findBySymbol} 對應 unique 欄位。
 * 【概念】每標的一筆持倉；風控與成交後更新都先靠 symbol 定位列。
 * 【邊界】不含持倉演算法。
 */
public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    /** 依標的查單筆持倉。 */
    Optional<PositionEntity> findBySymbol(String symbol);
}
