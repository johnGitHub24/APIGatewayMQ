package com.trading.application;

import com.trading.domain.OrderSide;
import com.trading.infrastructure.entity.PositionEntity;
import com.trading.infrastructure.repository.PositionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 【職責】保護 {@link PositionService} 查詢、曝險與成交後持倉更新。
 * 【技巧】Mock Repository；真實 {@link PnLCalculator} 可選，此處 mock 未實現損益。
 * 【概念】持倉是風控輸入也是成交輸出，單測鎖定加減倉與均價語意。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private PnLCalculator pnlCalculator;

    @InjectMocks
    private PositionService positionService;

    @Test
    void findAll_delegatesToRepository() {
        when(positionRepository.findAll()).thenReturn(List.of(new PositionEntity()));

        assertThat(positionService.findAll()).hasSize(1);
    }

    @Test
    void findBySymbol_missing_throws() {
        when(positionRepository.findBySymbol("NONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> positionService.findBySymbol("NONE"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findBySymbol_found_returnsEntity() {
        PositionEntity entity = new PositionEntity();
        entity.setSymbol("BTCUSDT");
        when(positionRepository.findBySymbol("BTCUSDT")).thenReturn(Optional.of(entity));

        assertThat(positionService.findBySymbol("BTCUSDT")).isSameAs(entity);
    }

    @Test
    void getCurrentQuantity_missing_returnsZero() {
        when(positionRepository.findBySymbol("BTCUSDT")).thenReturn(Optional.empty());

        assertThat(positionService.getCurrentQuantity("BTCUSDT")).isEqualByComparingTo("0");
    }

    @Test
    void getTotalExposure_sumsAbsoluteNotional() {
        PositionEntity a = new PositionEntity();
        a.setQuantity(new BigDecimal("2"));
        a.setMarkPrice(new BigDecimal("10"));
        a.setAvgPrice(new BigDecimal("9"));
        PositionEntity b = new PositionEntity();
        b.setQuantity(new BigDecimal("-1"));
        b.setMarkPrice(null);
        b.setAvgPrice(new BigDecimal("20"));
        when(positionRepository.findAll()).thenReturn(List.of(a, b));

        assertThat(positionService.getTotalExposure()).isEqualByComparingTo("40");
    }

    @Test
    void updateAfterFill_buyCreatesPositionAndWeightedAvg() {
        when(positionRepository.findBySymbol("BTCUSDT")).thenReturn(Optional.empty());
        when(pnlCalculator.calculateUnrealized(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PositionEntity saved = positionService.updateAfterFill(
                "BTCUSDT", OrderSide.BUY, new BigDecimal("1"), new BigDecimal("100"));

        assertThat(saved.getQuantity()).isEqualByComparingTo("1");
        assertThat(saved.getAvgPrice()).isEqualByComparingTo("100");
        assertThat(saved.getMarkPrice()).isEqualByComparingTo("100");
    }

    @Test
    void updateAfterFill_sellKeepsAvgPrice() {
        PositionEntity existing = new PositionEntity();
        existing.setSymbol("BTCUSDT");
        existing.setQuantity(new BigDecimal("2"));
        existing.setAvgPrice(new BigDecimal("50"));
        when(positionRepository.findBySymbol("BTCUSDT")).thenReturn(Optional.of(existing));
        when(pnlCalculator.calculateUnrealized(any(), any(), any())).thenReturn(new BigDecimal("10"));
        when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PositionEntity saved = positionService.updateAfterFill(
                "BTCUSDT", OrderSide.SELL, new BigDecimal("1"), new BigDecimal("60"));

        assertThat(saved.getQuantity()).isEqualByComparingTo("1");
        assertThat(saved.getAvgPrice()).isEqualByComparingTo("50");
        assertThat(saved.getUnrealizedPnl()).isEqualByComparingTo("10");
    }
}
