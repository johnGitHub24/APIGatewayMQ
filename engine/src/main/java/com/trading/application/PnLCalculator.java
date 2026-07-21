package com.trading.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 【職責】未實現損益純計算：數量 ×（標記價 − 均價）。
 * 【技巧】無狀態 {@code @Component}；null 輸入回 {@link BigDecimal#ZERO}。
 * 【概念】把公式抽成獨立類，方便單測與被持倉服務重用，不必綁 DB。
 * 【邊界】不讀寫資料庫、不決定 markPrice 來源。
 */
@Component
public class PnLCalculator {

    /**
     * 【職責】依持倉數量、均價與標記價計算未實現損益。
     * 【技巧】{@code quantity.multiply(markPrice.subtract(avgPrice))}。
     * 【概念】多頭：市價高於成本為正；空頭數量為負時符號自然反映。
     * @param quantity  持倉數量（可為負表示空）
     * @param avgPrice  平均成本
     * @param markPrice 標記／市價
     * @return 未實現損益；任一參數 null 則 0
     */
    public BigDecimal calculateUnrealized(BigDecimal quantity, BigDecimal avgPrice, BigDecimal markPrice) {
        if (quantity == null || avgPrice == null || markPrice == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(markPrice.subtract(avgPrice));
    }
}
