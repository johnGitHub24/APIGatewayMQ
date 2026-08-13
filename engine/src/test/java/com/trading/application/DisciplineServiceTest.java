package com.trading.application;

import com.trading.config.RiskProperties;
import com.trading.domain.MarketContext;
import com.trading.domain.OrderEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 【職責】保護 {@link DisciplineService#evaluate} 僅在頻率超標時寫紀律事件。
 * 【技巧】調整 {@link RiskProperties#getDisciplineOrderThreshold()} 對照 recentOrderCount。
 * 【概念】紀律標記不是硬拒單，與 R006 硬上限分層。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DisciplineServiceTest {

    @Mock
    private OrderEventService orderEventService;

    private RiskProperties riskProperties;
    private DisciplineService disciplineService;

    @BeforeEach
    void setUp() {
        riskProperties = new RiskProperties();
        riskProperties.setDisciplineOrderThreshold(5);
        disciplineService = new DisciplineService(riskProperties, orderEventService);
    }

    @Test
    void evaluate_belowThreshold_doesNotLog() {
        MarketContext market = new MarketContext("BTCUSDT", new BigDecimal("0.30"),
                true, new BigDecimal("0.60"), false, new BigDecimal("0.20"), 4);

        disciplineService.evaluate(1L, market);

        verify(orderEventService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void evaluate_atThreshold_logsDisciplineFlag() {
        MarketContext market = new MarketContext("BTCUSDT", new BigDecimal("0.30"),
                true, new BigDecimal("0.60"), false, new BigDecimal("0.20"), 5);

        disciplineService.evaluate(2L, market);

        verify(orderEventService).log(eq(2L), eq(OrderEventType.DISCIPLINE_FLAG), eq("R006"),
                eq("High order frequency in window"), isNull());
    }
}
