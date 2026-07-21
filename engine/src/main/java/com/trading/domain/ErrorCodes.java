package com.trading.domain;

/**
 * 【職責】集中定義 Engine API／風控錯誤碼字串，供例外與 Problem JSON 的 {@code errorCode} 使用。
 * 【技巧】{@code public static final String} 常數類；私有建構子防止實例化。
 * 【概念】錯誤碼是「機器可讀契約」：前端／測試可依碼分支，訊息則可本地化。
 *         若到處硬編碼字串，重構時易漏改；集中常數讓 IDE 能找到所有引用。
 * 【邊界】不負責 HTTP 狀態碼映射（由 {@link com.trading.config.GlobalExceptionHandler} 決定）。
 */
public final class ErrorCodes {

    /** 工具類不應被實例化。 */
    private ErrorCodes() {}

    /** 請求驗證失敗。 */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    /** 訂單不存在。 */
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    /** 持倉不存在。 */
    public static final String POSITION_NOT_FOUND = "POSITION_NOT_FOUND";
    /** HTTP 方法不被支援。 */
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    /** 風控：單標的持倉超限。 */
    public static final String RISK_POSITION_LIMIT = "RISK_POSITION_LIMIT";
    /** 風控：總曝險超限。 */
    public static final String RISK_EXPOSURE_LIMIT = "RISK_EXPOSURE_LIMIT";
    /** 風控：下單參數非法。 */
    public static final String RISK_INVALID_ORDER = "RISK_INVALID_ORDER";
    /** 重複下單（冪等／內容查重）。 */
    public static final String DUPLICATE_ORDER = "DUPLICATE_ORDER";
    /** 風控：波動過高。 */
    public static final String RISK_VOLATILITY_LIMIT = "RISK_VOLATILITY_LIMIT";
    /** 風控：短時窗下單過密。 */
    public static final String RISK_OVERTRADING = "RISK_OVERTRADING";
    /** 風控：市場盤整，不適合交易。 */
    public static final String RISK_MARKET_CHOPPY = "RISK_MARKET_CHOPPY";
    /** 風控：趨勢不足。 */
    public static final String RISK_TREND_WEAK = "RISK_TREND_WEAK";
    /** 風控：訊號噪音過高。 */
    public static final String RISK_SIGNAL_NOISY = "RISK_SIGNAL_NOISY";
    /** 訂單狀態不允許取消。 */
    public static final String ORDER_NOT_CANCELLABLE = "ORDER_NOT_CANCELLABLE";
    /** 訂單狀態不允許補成交。 */
    public static final String ORDER_NOT_FILLABLE = "ORDER_NOT_FILLABLE";
    /** 成交不存在。 */
    public static final String TRADE_NOT_FOUND = "TRADE_NOT_FOUND";
    /** 未預期內部錯誤。 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
