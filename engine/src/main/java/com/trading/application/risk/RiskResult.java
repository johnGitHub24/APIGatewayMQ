package com.trading.application.risk;

import com.trading.domain.OrderSide;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 【職責】風控評估結果模型：放行、帶數量調整的放行、或拒絕（含錯誤碼／規則碼／說明）。
 * 【技巧】私有建構子 + 靜態工廠；{@code @Getter}；內嵌 {@link OrderRiskContext} record 當輸入。
 * 【概念】用不可變結果物件傳遞「決策」，避免布林 + 一堆 out 參數。
 * 【邊界】不含 HTTP／持久化語意；由 TradingService 解讀。
 */
@Getter
public class RiskResult {

    private final boolean approved;
    private final String errorCode;
    private final String ruleCode;
    private final String detail;
    private final BigDecimal adjustedQuantity;

    /** 內部建構子：由靜態工廠統一建立。 */
    private RiskResult(boolean approved, String errorCode, String ruleCode, String detail,
                       BigDecimal adjustedQuantity) {
        this.approved = approved;
        this.errorCode = errorCode;
        this.ruleCode = ruleCode;
        this.detail = detail;
        this.adjustedQuantity = adjustedQuantity;
    }

    /**
     * 【職責】建立無條件放行結果。
     * 【技巧】靜態工廠隱藏建構細節。
     * 【概念】規則「沒事」時的標準回傳。
     */
    public static RiskResult approve() {
        return new RiskResult(true, null, null, null, null);
    }

    /**
     * 【職責】建立放行但建議調整數量的結果。
     * 【技巧】把 adjustedQuantity 帶在同一物件。
     * 【概念】軟風控：不拒單，但縮小曝險（如 R010）。
     */
    public static RiskResult approveWithQuantityAdjustment(BigDecimal adjustedQuantity) {
        return new RiskResult(true, null, null, null, adjustedQuantity);
    }

    /**
     * 【職責】建立拒絕結果。
     * 【技巧】同時帶 errorCode 與 ruleCode，方便 API 與稽核。
     * 【概念】硬風控：這筆單不能過。
     */
    public static RiskResult reject(String errorCode, String ruleCode, String detail) {
        return new RiskResult(false, errorCode, ruleCode, detail, null);
    }

    /** 是否含數量調整建議。 */
    public boolean hasQuantityAdjustment() {
        return adjustedQuantity != null;
    }

    /**
     * 【職責】下單風控所需的不可變輸入上下文。
     * 【技巧】Java record 承載訂單欄位與當前持倉／總曝險。
     * 【概念】把「規則要看的資料」一次打包，避免 evaluate 參數列表過長。
     */
    public record OrderRiskContext(
            String clientOrderId,
            String symbol,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal currentPositionQty,
            BigDecimal totalExposure
    ) {}
}
