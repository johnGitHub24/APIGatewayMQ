package com.trading.application.risk;

import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 【職責】R007 LOC（局）— 市場適合性：盤整過度（choppy）則拒單。
 * 【技巧】{@code @Order(3)}；讀 {@link MarketContext#choppy()}。
 * 【概念】震盪市假訊號多，紀律上可選擇「不適合就空手」——這是情境過濾，不是價格驗證。
 * 【邊界】不判斷趨勢強弱（見 R008）；choppy 旗標由 {@link com.trading.application.MarketService} 提供。
 */
@Component
@Order(3)
public class MarketSuitabilityRule implements RiskRule {

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R007";
    }

    /**
     * 【職責】若市場標記為 choppy 則拒絕下單。
     * 【技巧】布林旗標直接對應拒絕結果。
     * 【概念】把「局」做成可測規則：測試用 symbol 後綴 CHOP 即可重現。
     * @param context 訂單上下文
     * @param market  市場情境
     * @return choppy 則拒絕，否則放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        if (market.choppy()) {
            return RiskResult.reject(
                    ErrorCodes.RISK_MARKET_CHOPPY, ruleCode(),
                    "Market too choppy for symbol " + context.symbol());
        }
        return RiskResult.approve();
    }
}
