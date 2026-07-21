package com.trading.application.risk;

import com.trading.config.RiskProperties;
import com.trading.domain.ErrorCodes;
import com.trading.domain.MarketContext;
import com.trading.infrastructure.repository.OrderRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 【職責】R004 重複下單檢查：冪等鍵查重，否則用內容＋時窗近似查重。
 * 【技巧】{@code @Order(2)}；先 {@code findByClientOrderId}，再 {@code existsBySymbolAndSide...After}。
 * 【概念】網路重送很常見；冪等鍵是精確去重，內容時窗是沒有鍵時的保底。
 * 【邊界】不建單；重複時回 DUPLICATE_ORDER 供上層決定是否再寫拒單列。
 */
@Component
@Order(2)
public class DuplicateCheckRule implements RiskRule {

    private final RiskProperties riskProperties;
    private final OrderRepository orderRepository;

    /** 注入重複時窗設定與訂單查詢。 */
    public DuplicateCheckRule(RiskProperties riskProperties, OrderRepository orderRepository) {
        this.riskProperties = riskProperties;
        this.orderRepository = orderRepository;
    }

    /** {@inheritDoc} */
    @Override
    public String ruleCode() {
        return "R004";
    }

    /**
     * 【職責】執行查重：有 clientOrderId 則鍵值查重，否則內容時窗查重。
     * 【技巧】空白鍵視為未提供；時窗用 {@code now.minusSeconds(duplicateWindow)}。
     * 【概念】有冪等鍵時「同鍵＝同單」；無鍵時「短時間同內容」視為可疑重送。
     * @param context 訂單上下文
     * @param market  市場情境（本規則未使用）
     * @return 重複則拒絕，否則放行
     */
    @Override
    public RiskResult evaluate(RiskResult.OrderRiskContext context, MarketContext market) {
        if (context.clientOrderId() != null && !context.clientOrderId().isBlank()) {
            if (orderRepository.findByClientOrderId(context.clientOrderId()).isPresent()) {
                return RiskResult.reject(
                        ErrorCodes.DUPLICATE_ORDER, ruleCode(),
                        "Duplicate order with same Idempotency-Key");
            }
            return RiskResult.approve();
        }

        OffsetDateTime since = OffsetDateTime.now().minusSeconds(riskProperties.getDuplicateWindowSeconds());
        if (orderRepository.existsBySymbolAndSideAndQuantityAndPriceAndCreatedAtAfter(
                context.symbol(), context.side(), context.quantity(), context.price(), since)) {
            return RiskResult.reject(
                    ErrorCodes.DUPLICATE_ORDER, ruleCode(),
                    "Duplicate order content within " + riskProperties.getDuplicateWindowSeconds() + " seconds");
        }
        return RiskResult.approve();
    }
}
