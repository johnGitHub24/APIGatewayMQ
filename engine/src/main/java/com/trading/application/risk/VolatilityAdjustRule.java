package com.trading.application.risk;

import com.trading.config.RiskProperties;
import com.trading.domain.MarketContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 【職責】R010 FENG（風）— 波動偏高時縮小下單量（軟風控，非極端拒絕）。
 * 【技巧】{@code @Order(6)}；落在 reduce～max 區間則 {@code quantity * reduceRatio} 回調整放行。
 * 【概念】與 R005「極端波動硬擋」互補：中等偏高波動用縮量控風險，仍允許交易。
 * 【邊界】不拒絕；極端超 max 由 VolatilityRule 處理。調整後數量須仍 &gt; 0 且小於原量。
 */
@Component
@Order(6)
public class VolatilityAdjustRule implements RiskRule {

    private final RiskProperties riskProperties;

    /** 注入波動調整相關設定。 */
    public VolatilityAdjustRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R010";
    }

    /**
     * 【職責】在可交易但偏高波動區間，回傳建議縮量的放行結果。
     * 【技巧】區間判斷 + {@code setScale(8, HALF_UP)}；否則純 {@code approve()}。
     * 【概念】軟風控回「帶建議的通過」，由 TradingService 改用 adjustedQuantity 建單。
     * @param context 訂單上下文
     * @param market  含 volatilityIndex 的市場情境
     * @return 縮量放行或原樣放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        if (market.volatilityIndex().compareTo(riskProperties.getReduceVolatilityThreshold()) >= 0
                && market.volatilityIndex().compareTo(riskProperties.getMaxVolatilityIndex()) <= 0) {
            BigDecimal reduced = context.quantity()
                    .multiply(riskProperties.getVolatilityReduceRatio())
                    .setScale(8, RoundingMode.HALF_UP);
            if (reduced.compareTo(BigDecimal.ZERO) > 0
                    && reduced.compareTo(context.quantity()) < 0) {
                return RiskResult.approveWithQuantityAdjustment(reduced);
            }
        }
        return RiskResult.approve();
    }
}
