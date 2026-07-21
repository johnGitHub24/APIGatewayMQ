package com.trading.application.risk;

import com.trading.config.RiskProperties;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 【職責】R006 過度交易：短時間下單次數達上限則硬拒。
 * 【技巧】{@code @Order(10)}；比較 {@link MarketContext#recentOrderCount()} 與 {@code maxOrdersPerWindow}。
 * 【概念】頻率限制保護帳戶與系統；與 DisciplineService 的「只標記」不同，這裡直接擋。
 * 【邊界】視窗長度由 MarketService／設定決定；本規則只做門檻比較。
 */
@Component
@Order(10)
public class OvertradingRule implements RiskRule {

    private final RiskProperties riskProperties;

    /** 注入時間窗內最大下單數設定。 */
    public OvertradingRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R006";
    }

    /**
     * 【職責】短時間下單數過高則拒單。
     * 【技巧】{@code >= maxOrdersPerWindow} 觸發拒絕。
     * 【概念】「手癢」是行為風險；用計數硬擋比事後檢討有效。
     * @param context 訂單上下文
     * @param market  含 recentOrderCount 的市場情境
     * @return 超頻拒絕或放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        if (market.recentOrderCount() >= riskProperties.getMaxOrdersPerWindow()) {
            return RiskResult.reject(
                    ErrorCodes.RISK_OVERTRADING, ruleCode(),
                    "Too many orders in short window");
        }
        return RiskResult.approve();
    }
}
