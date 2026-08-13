package com.trading.application;

import com.trading.domain.OrderEventType;
import com.trading.infrastructure.entity.PositionEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/**
 * 【職責】保護 {@link OutcomeRecordService#recordOutcome} 寫入結果事件。
 * 【技巧】Captor 檢查 payload JSON 含成交與持倉欄位。
 * 【概念】閉環要留下「成交當下的倉與未實現」，方便事後回顧。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OutcomeRecordServiceTest {

    @Mock
    private OrderEventService orderEventService;

    @InjectMocks
    private OutcomeRecordService outcomeRecordService;

    @Test
    void recordOutcome_logsPayloadWithPositionSnapshot() {
        PositionEntity position = new PositionEntity();
        position.setQuantity(new BigDecimal("1.5"));
        position.setUnrealizedPnl(new BigDecimal("20"));

        outcomeRecordService.recordOutcome(3L, "BTCUSDT", new BigDecimal("0.5"),
                new BigDecimal("65000"), position);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(orderEventService).log(eq(3L), eq(OrderEventType.OUTCOME_RECORDED),
                isNull(), isNull(), payload.capture());
        assertThat(payload.getValue()).contains("BTCUSDT", "0.5", "65000", "1.5", "20");
    }
}
