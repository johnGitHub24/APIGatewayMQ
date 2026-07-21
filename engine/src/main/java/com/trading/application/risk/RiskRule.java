package com.trading.application.risk;

import com.trading.domain.MarketContext;

/**
 * 【職責】單一風控規則介面（Strategy）：只判斷自己的條件並回傳 {@link RiskResult}。
 * 【技巧】Strategy Pattern；由 {@link RiskEngineImpl} 注入 {@code List<RiskRule>} 串接。
 * 【概念】每條規則可獨立測試與替換；引擎負責順序與 fail-fast，規則不互相呼叫。
 * 【邊界】不持久化、不發事件；只回傳放行／拒絕／調整建議。
 */
public interface RiskRule {

    /** 規則代碼（例如 R001），供日誌與錯誤回應追蹤。 */
    String ruleCode();

    /**
     * 【職責】對一筆下單上下文執行本規則評估。
     * 【技巧】輸入 {@link RiskResult.OrderRiskContext} + {@link MarketContext}；輸出不可變 {@link RiskResult}。
     * 【概念】規則應盡量無副作用，同一輸入得到同一結果，方便單測與重播。
     * @param context 訂單與持倉／曝險投影輸入
     * @param market  市場情境
     * @return 放行、拒絕或帶數量調整的放行
     */
    RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market);
}
