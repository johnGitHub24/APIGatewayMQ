package com.trading.application;

import com.trading.config.RiskProperties;
import com.trading.domain.MarketContext;
import com.trading.domain.OrderEventType;
import com.trading.infrastructure.entity.PositionEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 【職責】GUO（果）— 記錄成交結果與績效快照事件。
 * 【技巧】組 JSON payload 後寫 {@link OrderEventType#OUTCOME_RECORDED}。
 * 【概念】閉環交易強調「結果可回顧」：成交當下把部位與未實現損益一併留下。
 * 【邊界】不更新持倉本身（由 {@link PositionService}）；只寫事件。
 */
@Service
public class OutcomeRecordService {

    private final OrderEventService orderEventService;

    /** 建構子注入結果紀錄依賴。 */
    public OutcomeRecordService(OrderEventService orderEventService) {
        this.orderEventService = orderEventService;
    }

    /**
     * 【職責】記錄一筆交易結果事件（供閉環／統計）。
     * 【技巧】{@code String.format} 組簡易 JSON；委派 {@link OrderEventService#log}。
     * 【概念】把「成交量、價、當下倉、未實現」綁在同一事件，事後分析不必再 join 多表推估。
     * @param orderId  訂單主鍵
     * @param symbol   標的
     * @param fillQty  本次成交量
     * @param price    成交價
     * @param position 更新後持倉
     */
    public void recordOutcome(Long orderId, String symbol, BigDecimal fillQty, BigDecimal price,
                            PositionEntity position) {
        String payload = String.format(
                "{\"symbol\":\"%s\",\"fillQty\":%s,\"price\":%s,\"positionQty\":%s,\"unrealizedPnl\":%s}",
                symbol, fillQty, price, position.getQuantity(), position.getUnrealizedPnl());
        orderEventService.log(orderId, OrderEventType.OUTCOME_RECORDED, null, null, payload);
    }
}
