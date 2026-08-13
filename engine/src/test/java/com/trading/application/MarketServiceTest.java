package com.trading.application;

import com.trading.config.RiskProperties;
import com.trading.domain.MarketContext;
import com.trading.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 【職責】保護 {@link MarketService#getContext(String)} 教學用後綴推導。
 * 【技巧】固定 {@link RiskProperties} 預設值；Mock 近期下單次數。
 * 【概念】風控規則是純函數，市場上下文必須可重現。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private MarketService marketService;

    @BeforeEach
    void setUp() {
        marketService = new MarketService(new RiskProperties(), orderRepository);
        when(orderRepository.countByCreatedAtAfter(any())).thenReturn(4L);
    }

    @Test
    void getContext_plainSymbol_usesDefaultVolatility() {
        MarketContext context = marketService.getContext("BTCUSDT");

        assertThat(context.volatilityIndex()).isEqualByComparingTo("0.30");
        assertThat(context.choppy()).isFalse();
        assertThat(context.recentOrderCount()).isEqualTo(4L);
    }

    @Test
    void getContext_highVolSuffix_raisesVolatility() {
        MarketContext context = marketService.getContext("ETHHIGHVOL");

        assertThat(context.volatilityIndex()).isGreaterThan(new BigDecimal("0.65"));
    }

    @Test
    void getContext_chopSuffix_marksChoppy() {
        MarketContext context = marketService.getContext("BTCCHOP");

        assertThat(context.choppy()).isTrue();
        assertThat(context.trending()).isFalse();
    }
}
