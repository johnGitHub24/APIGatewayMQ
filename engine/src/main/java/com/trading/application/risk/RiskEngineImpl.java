package com.trading.application.risk;

import com.trading.domain.MarketContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 【職責】風控引擎預設實作：依序執行所有 {@link RiskRule}，fail-fast 拒絕，並彙總數量調整。
 * 【技巧】建構子注入 {@code List<RiskRule>}（Spring 收集全部規則 Bean）；{@code @Order} 影響順序。
 * 【概念】第一條拒絕就停——後面規則不必跑；多條調整時後者覆蓋前者的 adjustedQuantity。
 * 【邊界】不決定規則內容；規則本身各自實作 evaluate。
 */
@Component
public class RiskEngineImpl implements RiskEngine {

    private final List<RiskRule> rules;

    /** Spring 自動注入所有 {@link RiskRule} Bean。 */
    public RiskEngineImpl(List<RiskRule> rules) {
        this.rules = rules;
    }

    /**
     * 【職責】串行評估全部規則並回傳最終結果。
     * 【技巧】迴圈 {@code evaluate}；未核准立即 {@code return}；最後有調整則 {@code approveWithQuantityAdjustment}。
     * 【概念】Chain of Responsibility／Pipeline：每關通過才進下一關，全部過才放行。
     * @param context 訂單風控輸入
     * @param market  市場情境
     * @return 拒絕、純放行、或帶縮量的放行
     */
    @Override
    public RiskResult validate(RiskResult.OrderRiskContext context, MarketContext market) {
        BigDecimal adjustedQuantity = null;
        // 依序執行規則；可依 @Order 或 Bean 載入順序控制先後（若專案有設定）
        for (RiskRule rule : rules) {
            RiskResult result = rule.evaluate(context, market);
            if (!result.isApproved()) {
                return result;
            }
            if (result.hasQuantityAdjustment()) {
                adjustedQuantity = result.getAdjustedQuantity();
            }
        }
        if (adjustedQuantity != null) {
            return RiskResult.approveWithQuantityAdjustment(adjustedQuantity);
        }
        return RiskResult.approve();
    }
}
