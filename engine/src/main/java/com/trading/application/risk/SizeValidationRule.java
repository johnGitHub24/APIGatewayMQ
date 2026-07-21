package com.trading.application.risk;

import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 【職責】R003 基礎欄位驗證：quantity／price 必須存在且大於 0。
 * 【技巧】{@code @Order(1)} 最先執行；{@code compareTo(ZERO) <= 0} 擋非法值。
 * 【概念】先擋壞資料，後面規則才不會用 null／負數做曝險投影。
 * 【邊界】不做業務上限（那是持倉／曝險規則）；只做「基本合法」。
 */
@Component
@Order(1)
public class SizeValidationRule implements RiskRule {

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R003";
    }

    /**
     * 【職責】驗證 quantity／price 非 null 且大於零。
     * 【技巧】短路條件一次檢查四種非法情況。
     * 【概念】風控鏈的第一道門：格式／基本語意不過，後面規則免談。
     * @param context 訂單上下文
     * @param market  市場情境（本規則未使用）
     * @return 非法則拒絕，否則放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        if (context.quantity() == null || context.price() == null
                || context.quantity().compareTo(BigDecimal.ZERO) <= 0
                || context.price().compareTo(BigDecimal.ZERO) <= 0) {
            return RiskResult.reject(
                    ErrorCodes.RISK_INVALID_ORDER, ruleCode(),
                    "Quantity and price must be greater than zero");
        }
        return RiskResult.approve();
    }
}
