package com.trading.application.risk;

import com.trading.config.RiskProperties;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 【職責】R005 極端波動拒單：波動指數超過上限則硬擋。
 * 【技巧】{@code @Order(9)}；{@code volatilityIndex > maxVolatilityIndex} 則拒絕。
 * 【概念】市場失控時優先保命；與 R010 縮量不同，這裡連縮量都不給過。
 * 【邊界】不調整數量；中等波動留給 VolatilityAdjustRule。
 */
@Component
@Order(9)
public class VolatilityRule implements RiskRule {

    private final RiskProperties riskProperties;

    /** 注入最大波動容忍值。 */
    public VolatilityRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R005";
    }

    /**
     * 【職責】波動指數超上限時拒單。
     * 【技巧】單一門檻比較。
     * 【概念】教學用 VOL 後綴可把波動拉到極端以觸發本規則。
     * @param context 訂單上下文
     * @param market  含 volatilityIndex 的市場情境
     * @return 極端波動拒絕或放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        if (market.volatilityIndex().compareTo(riskProperties.getMaxVolatilityIndex()) > 0) {
            return RiskResult.reject(
                    ErrorCodes.RISK_VOLATILITY_LIMIT, ruleCode(),
                    "Market volatility too high for symbol " + context.symbol());
        }
        return RiskResult.approve();
    }
}
