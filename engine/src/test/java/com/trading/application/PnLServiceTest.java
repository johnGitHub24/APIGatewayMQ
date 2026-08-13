package com.trading.application;

import com.trading.dto.PnLResponse;
import com.trading.infrastructure.entity.PositionEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 【職責】保護 {@link PnLService#getSummary()} 彙總未實現損益。
 * 【技巧】Mock {@link PositionService#findAll()} 提供兩筆持倉。
 * 【概念】API 要的是帳戶視角加總，公式已存在持倉列上。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PnLServiceTest {

    @Mock
    private PositionService positionService;

    @InjectMocks
    private PnLService pnlService;

    @Test
    void getSummary_sumsUnrealizedAndMapsItems() {
        PositionEntity a = new PositionEntity();
        a.setSymbol("BTCUSDT");
        a.setUnrealizedPnl(new BigDecimal("10"));
        PositionEntity b = new PositionEntity();
        b.setSymbol("ETHUSDT");
        b.setUnrealizedPnl(new BigDecimal("-3"));
        when(positionService.findAll()).thenReturn(List.of(a, b));

        PnLResponse response = pnlService.getSummary();

        assertThat(response.getTotalUnrealizedPnl()).isEqualByComparingTo("7");
        assertThat(response.getPositions()).hasSize(2);
        assertThat(response.getAsOf()).isNotNull();
    }
}
