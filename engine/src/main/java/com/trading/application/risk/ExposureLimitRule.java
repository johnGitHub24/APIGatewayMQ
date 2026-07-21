package com.trading.application.risk;

import com.trading.config.RiskProperties;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 【職責】R002 總曝險上限：既有曝險＋本單名目不得超過上限。
 * 【技巧】{@code @Order(8)}；{@code quantity * price} 當本單曝險，與 {@code totalExposure} 相加比較。
 * 【概念】單一標的沒爆、帳戶整體仍可能過大——需要「組合風險」上限。
 * 【邊界】不計算波動調整後的風險值（VaR）；用簡化名目曝險教學。
 */
@Component
@Order(8)
public class ExposureLimitRule implements RiskRule {

    private final RiskProperties riskProperties;

    /** 注入總曝險上限設定。 */
    public ExposureLimitRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R002";
    }

    /**
     * 【職責】驗證投影後總曝險是否超標。
     * 【技巧】{@code compareTo(maxTotalExposure) > 0} 則拒絕。
     * 【概念】「投影」＝假設本單成交後的曝險，事前擋比事後砍倉便宜。
     * @param context 含 totalExposure 的訂單上下文
     * @param market  市場情境（本規則未使用）
     * @return 超限拒絕或放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        BigDecimal orderExposure = context.quantity().multiply(context.price());
        BigDecimal projectedExposure = context.totalExposure().add(orderExposure);
        if (projectedExposure.compareTo(riskProperties.getMaxTotalExposure()) > 0) {
            return RiskResult.reject(
                    ErrorCodes.RISK_EXPOSURE_LIMIT, ruleCode(),
                    "Total exposure limit exceeded");
        }
        return RiskResult.approve();
    }
}
