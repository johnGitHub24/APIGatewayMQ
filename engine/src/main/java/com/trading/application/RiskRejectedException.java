package com.trading.application;

/**
 * 【職責】風控拒單例外：訂單未通過 {@link com.trading.application.risk.RiskEngine}。
 * 【技巧】攜帶 {@code errorCode} 與 {@code ruleCode}；Consumer 捕捉後不進 DLQ。
 * 【概念】「拒絕」是合法業務結果，不是系統故障——重試同一拒單沒有意義。
 * 【邊界】不寫 REJECTED 訂單（由 {@link TradingService} 在拋出前處理）；不組 HTTP。
 */
public class RiskRejectedException extends RuntimeException {

    private final String errorCode;
    private final String ruleCode;

    /** 建立例外，附帶錯誤碼、觸發規則代碼與拒絕原因。 */
    public RiskRejectedException(String errorCode, String ruleCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
        this.ruleCode = ruleCode;
    }

    /** 取得業務錯誤碼（例如 DUPLICATE_ORDER）。 */
    public String getErrorCode() {
        return errorCode;
    }

    /** 取得觸發拒絕的風控規則代碼（例如 R001）。 */
    public String getRuleCode() {
        return ruleCode;
    }
}
