package com.trading.domain;

import java.math.BigDecimal;

/**
 * 【職責】承載市場情境快照（局位勢機訊 — LOC/WEI/SHI/XUN 的工程化表示），供風控規則唯讀使用。
 * 【技巧】Java {@code record}：不可變資料載體，自動產生建構子／accessor／equals。
 * 【概念】風控不應各自去「猜市場」；由 MarketService 組裝一次，規則只讀欄位做判斷。
 *         Record 比可變 POJO 更適合「當下快照」語意。
 * 【邊界】不負責持久化或即時行情訂閱；數值來源由應用層決定。
 */
public record MarketContext(
        String symbol,
        BigDecimal volatilityIndex,
        boolean trending,
        BigDecimal trendStrength,
        boolean choppy,
        BigDecimal signalNoise,
        long recentOrderCount
) {
    /**
     * 【職責】建立中性預設情境（測試／預設路徑）。
     * 【技巧】靜態工廠方法 {@code neutral(symbol)}，隱藏魔術數字預設值。
     * 【概念】測試與「無特殊行情」路徑需要可重現的基準；工廠比到處 new 一長串參數更不易漏欄。
     *
     * @param symbol 商品代碼
     * @return 中性市場情境
     */
    public static MarketContext neutral(String symbol) {
        return new MarketContext(
                symbol,
                new BigDecimal("0.30"),
                true,
                new BigDecimal("0.60"),
                false,
                new BigDecimal("0.20"),
                0);
    }
}
