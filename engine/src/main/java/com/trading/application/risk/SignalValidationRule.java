package com.trading.application.risk;

import com.trading.config.RiskProperties;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 【職責】R009 XUN（訊）— 訊號可信度：噪音過高則拒單。
 * 【技巧】{@code @Order(5)}；比較 {@link MarketContext#signalNoise()} 與 {@code maxSignalNoise}。
 * 【概念】訊號品質差時進場等於賭博；先過濾噪音再談方向。
 * 【邊界】不驗證趨勢（R008）或波動（R005／R010）；只看噪音指標。
 */
@Component
@Order(5)
public class SignalValidationRule implements RiskRule {

    private final RiskProperties riskProperties;

    /** 注入噪音上限設定。 */
    public SignalValidationRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R009";
    }

    /**
     * 【職責】訊號噪音超過上限時拒單。
     * 【技巧】{@code compareTo(maxSignalNoise) > 0}。
     * 【概念】教學上用 symbol 後綴 NOISE 拉高指標，驗證規則會擋。
     * @param context 訂單上下文
     * @param market  含 signalNoise 的市場情境
     * @return 噪音過高拒絕或放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        if (market.signalNoise().compareTo(riskProperties.getMaxSignalNoise()) > 0) {
            return RiskResult.reject(
                    ErrorCodes.RISK_SIGNAL_NOISY, ruleCode(),
                    "Signal too noisy for symbol " + context.symbol());
        }
        return RiskResult.approve();
    }
}
