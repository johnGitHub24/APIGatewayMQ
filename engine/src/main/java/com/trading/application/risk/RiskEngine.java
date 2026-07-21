package com.trading.application.risk;

import com.trading.domain.MarketContext;

/**
 * 【職責】風控引擎抽象：對下單執行整體驗證並回傳聚合結果。
 * 【技巧】介面與實作分離，便於測試替換 {@link RiskEngineImpl}。
 * 【概念】下單流程只依賴此介面，不綁定「有哪些規則、什麼順序」。
 * 【邊界】不建單、不寫事件；只回答「能不能下、要不要縮量」。
 */
public interface RiskEngine {

    /**
     * 【職責】執行整體風控驗證。
     * 【技巧】傳入訂單上下文與市場情境，回傳單一 {@link RiskResult}。
     * 【概念】呼叫端看到的是「總結果」，不必知道內部跑了幾條規則。
     * @param context 訂單風控輸入
     * @param market  市場情境
     * @return 聚合後的放行／拒絕／調整結果
     */
    RiskResult validate(RiskResult.OrderRiskContext context, MarketContext market);
}
