package com.trading.application;

import com.trading.config.RiskProperties;
import com.trading.domain.MarketContext;
import com.trading.domain.OrderEventType;
import org.springframework.stereotype.Service;

/**
 * 【職責】DE（德）紀律層：依近期下單頻率標記過度交易行為事件。
 * 【技巧】讀 {@link RiskProperties#getDisciplineOrderThreshold()}；超過則寫 {@link OrderEventType#DISCIPLINE_FLAG}。
 * 【概念】與硬拒單的 OvertradingRule 不同：此處是「標記提醒」，不一定擋單——紀律與風控可分層。
 * 【邊界】不改訂單狀態、不拒單；只寫事件。硬上限見 risk 套件 R006。
 */
@Service
public class DisciplineService {

    private final RiskProperties riskProperties;
    private final OrderEventService orderEventService;

    /** 建構子注入風控設定與事件服務。 */
    public DisciplineService(RiskProperties riskProperties, OrderEventService orderEventService) {
        this.riskProperties = riskProperties;
        this.orderEventService = orderEventService;
    }

    /**
     * 【職責】依近期下單頻率評估是否寫入紀律標記事件。
     * 【技巧】比較 {@link MarketContext#recentOrderCount()} 與門檻；呼叫 {@link OrderEventService#log}。
     * 【概念】成交後再評估行為，讓稽核能看到「這筆成交當下頻率已偏高」。
     * @param orderId 訂單主鍵
     * @param market  市場／行為上下文
     */
    public void evaluate(Long orderId, MarketContext market) {
        if (market.recentOrderCount() >= riskProperties.getDisciplineOrderThreshold()) {
            orderEventService.log(orderId, OrderEventType.DISCIPLINE_FLAG, "R006",
                    "High order frequency in window", null);
        }
    }
}
