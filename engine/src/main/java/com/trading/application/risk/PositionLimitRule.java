package com.trading.application.risk;

import com.trading.config.RiskProperties;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import com.trading.domain.OrderSide;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 【職責】R001 單一標的持倉上限：投影持倉絕對值不得超過上限。
 * 【技巧】{@code @Order(7)}；BUY 加量／SELL 減量後取 {@code abs()} 比較。
 * 【概念】集中度風險：單一標的倉位過大，行情反向時損失失控。
 * 【邊界】不管帳戶總曝險（見 R002）；只看該 symbol。
 */
@Component
@Order(7)
public class PositionLimitRule implements RiskRule {

    private final RiskProperties riskProperties;

    /** 注入最大持倉限制設定。 */
    public PositionLimitRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R001";
    }

    /**
     * 【職責】計算下單後投影持倉，超上限則拒單。
     * 【技巧】私有 {@code projectedPosition}；{@code abs().compareTo(max)}。
     * 【概念】用「成交後會變多少」事前擋，而不是等倉位真的超了再砍。
     * @param context 含 currentPositionQty 的上下文
     * @param market  市場情境（本規則未使用）
     * @return 超限拒絕或放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        BigDecimal projectedQty = projectedPosition(context.currentPositionQty(), context.side(), context.quantity());
        if (projectedQty.abs().compareTo(riskProperties.getMaxPositionPerSymbol()) > 0) {
            return RiskResult.reject(
                    ErrorCodes.RISK_POSITION_LIMIT, ruleCode(),
                    "Position limit exceeded for symbol " + context.symbol());
        }
        return RiskResult.approve();
    }

    /** 依買賣方向計算下單後持倉。 */
    private BigDecimal projectedPosition(BigDecimal current, OrderSide side, BigDecimal qty) {
        BigDecimal base = current != null ? current : BigDecimal.ZERO;
        return side == OrderSide.BUY ? base.add(qty) : base.subtract(qty);
    }
}
