package com.trading.application;

import com.trading.config.RiskProperties;
import com.trading.infrastructure.entity.OrderEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 【職責】模擬撮合引擎：依門檻決定首次全成或部分成交，並規劃剩餘成交。
 * 【技巧】讀 {@link RiskProperties} 的 threshold／ratio；{@link BigDecimal} 運算與 {@link RoundingMode#HALF_UP}。
 * 【概念】真實撮合在交易所；此處用規則模擬「大單先吃一部分」，方便練習部分成交狀態機。
 * 【邊界】不寫 DB、不改訂單狀態；只回傳 {@link ExecutionResult} 計畫，由 {@link TradingService} 執行。
 */
@Component
public class ExecutionEngine {

    private final RiskProperties riskProperties;

    /** 建構子注入部分成交門檻設定。 */
    public ExecutionEngine(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    /**
     * 【職責】規劃首次成交量：超過門檻則部分成交，否則全成。
     * 【技巧】{@code compareTo} 比門檻；{@code multiply(ratio).setScale(8, HALF_UP)} 算部分量。
     * 【概念】部分成交讓訂單進入 PARTIALLY_FILLED，之後可用 completeFill 補足。
     * @param order 訂單實體
     * @return 成交計畫
     */
    public ExecutionResult planInitialFill(OrderEntity order) {
        BigDecimal threshold = riskProperties.getPartialFillThreshold();
        if (order.getQuantity().compareTo(threshold) < 0) {
            return ExecutionResult.fullFill(order.getQuantity());
        }
        BigDecimal partialQty = order.getQuantity()
                .multiply(riskProperties.getPartialFillRatio())
                .setScale(8, RoundingMode.HALF_UP);
        if (partialQty.compareTo(BigDecimal.ZERO) <= 0
                || partialQty.compareTo(order.getQuantity()) >= 0) {
            return ExecutionResult.fullFill(order.getQuantity());
        }
        return ExecutionResult.partialFill(partialQty, order.getQuantity().subtract(partialQty));
    }

    /**
     * 【職責】規劃剩餘數量的全額成交。
     * 【技巧】{@code quantity - alreadyFilled}；剩餘 ≤0 回 {@code none()}。
     * 【概念】第二次（或之後）補成交通常一次吃完剩餘，對應 completeFill 路徑。
     * @param order         訂單實體
     * @param alreadyFilled 已成交量
     * @return 成交計畫
     */
    public ExecutionResult planRemainingFill(OrderEntity order, BigDecimal alreadyFilled) {
        BigDecimal remaining = order.getQuantity().subtract(alreadyFilled);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return ExecutionResult.none();
        }
        return ExecutionResult.fullFill(remaining);
    }

    /**
     * 【職責】承載模擬撮合結果：本次成交量、剩餘量、是否已完全成交。
     * 【技巧】靜態工廠 {@code fullFill}/{@code partialFill}/{@code none} 表達語意。
     * 【概念】用不可變結果物件傳遞計畫，避免用多個 out 參數或陣列。
     */
    public record ExecutionResult(
            BigDecimal fillQty,
            BigDecimal remainingQty,
            boolean complete
    ) {
        static ExecutionResult fullFill(BigDecimal qty) {
            return new ExecutionResult(qty, BigDecimal.ZERO, true);
        }

        static ExecutionResult partialFill(BigDecimal fillQty, BigDecimal remaining) {
            return new ExecutionResult(fillQty, remaining, false);
        }

        static ExecutionResult none() {
            return new ExecutionResult(BigDecimal.ZERO, BigDecimal.ZERO, true);
        }
    }
}
