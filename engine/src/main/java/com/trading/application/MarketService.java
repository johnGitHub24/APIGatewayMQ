package com.trading.application;

import com.trading.config.RiskProperties;
import com.trading.domain.MarketContext;
import com.trading.infrastructure.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 【職責】組裝風控所需的 {@link MarketContext}（波動、趨勢、噪音、近期下單次數）。
 * 【技巧】依 symbol 後綴推導教學用指標；{@code countByCreatedAtAfter} 統計過度交易視窗。
 * 【概念】真實系統會接行情／訊號服務；此處用命名約定（HIGHVOL、CHOP…）讓測試可重現情境。
 * 【邊界】不執行風控規則（交給 {@link com.trading.application.risk.RiskEngine}）；只提供輸入上下文。
 */
@Service
public class MarketService {

    private final RiskProperties riskProperties;
    private final OrderRepository orderRepository;

    /** 建構子注入市場上下文依賴。 */
    public MarketService(RiskProperties riskProperties, OrderRepository orderRepository) {
        this.riskProperties = riskProperties;
        this.orderRepository = orderRepository;
    }

    /**
     * 【職責】依商品代碼取得當前市場情境。
     * 【技巧】{@code @Transactional(readOnly = true)}；組合 volatility／trend／noise／recentCount。
     * 【概念】風控規則是「純函數」：同一 context 應得到同一判斷，方便單測。
     * @param symbol 標的代碼（可含教學後綴）
     * @return 市場上下文
     */
    @Transactional(readOnly = true)
    public MarketContext getContext(String symbol) {
        BigDecimal volatility = resolveVolatility(symbol);
        boolean choppy = symbol != null && symbol.endsWith("CHOP");
        boolean trending = !choppy && volatility.compareTo(new BigDecimal("0.50")) > 0;
        BigDecimal trendStrength = resolveTrendStrength(symbol, trending);
        BigDecimal signalNoise = resolveSignalNoise(symbol);
        OffsetDateTime since = OffsetDateTime.now().minusSeconds(riskProperties.getOvertradingWindowSeconds());
        long recentCount = orderRepository.countByCreatedAtAfter(since);
        return new MarketContext(symbol, volatility, trending, trendStrength, choppy, signalNoise, recentCount);
    }

    private BigDecimal resolveVolatility(String symbol) {
        if (symbol == null) {
            return riskProperties.getDefaultVolatilityIndex();
        }
        if (symbol.endsWith("HIGHVOL")) {
            return riskProperties.getReduceVolatilityThreshold().add(new BigDecimal("0.05"));
        }
        if (symbol.endsWith("VOL")) {
            return riskProperties.getExtremeVolatilityIndex();
        }
        return riskProperties.getDefaultVolatilityIndex();
    }

    private BigDecimal resolveTrendStrength(String symbol, boolean trending) {
        if (symbol != null && symbol.endsWith("NOTREND")) {
            return new BigDecimal("0.25");
        }
        return trending ? new BigDecimal("0.70") : new BigDecimal("0.45");
    }

    private BigDecimal resolveSignalNoise(String symbol) {
        if (symbol != null && symbol.endsWith("NOISE")) {
            return new BigDecimal("0.85");
        }
        return new BigDecimal("0.20");
    }
}
