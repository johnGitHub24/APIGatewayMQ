package com.trading.application.risk;

import com.trading.config.RiskProperties;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 【職責】R008 SHI（勢）— 趨勢確認：趨勢強度低於門檻則拒單。
 * 【技巧】{@code @Order(4)}；比較 {@link MarketContext#trendStrength()} 與 {@code minTrendStrength}。
 * 【概念】順勢交易要先確認「有勢」；弱趨勢進場勝率差。
 * 【邊界】不判斷是否 choppy（R007）；強度來源由 MarketService 推導。
 */
@Component
@Order(4)
public class TrendConfirmationRule implements RiskRule {

    private final RiskProperties riskProperties;

    /** 注入趨勢門檻設定。 */
    public TrendConfirmationRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R008";
    }

    /**
     * 【職責】趨勢強度低於最低門檻時拒單。
     * 【技巧】{@code compareTo(minTrendStrength) < 0}。
     * 【概念】教學用 NOTREND 後綴可把強度壓低以觸發本規則。
     * @param context 訂單上下文
     * @param market  含 trendStrength 的市場情境
     * @return 趨勢不足拒絕或放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        if (market.trendStrength().compareTo(riskProperties.getMinTrendStrength()) < 0) {
            return RiskResult.reject(
                    ErrorCodes.RISK_TREND_WEAK, ruleCode(),
                    "Trend not confirmed for symbol " + context.symbol());
        }
        return RiskResult.approve();
    }
}
