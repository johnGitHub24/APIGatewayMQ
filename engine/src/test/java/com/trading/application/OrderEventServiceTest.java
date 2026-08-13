package com.trading.application;

import com.trading.domain.OrderEventType;
import com.trading.infrastructure.entity.OrderEventEntity;
import com.trading.infrastructure.repository.OrderEventRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 【職責】保護 {@link OrderEventService#log} 寫入審計事件欄位。
 * 【技巧】ArgumentCaptor 檢查 Entity 組裝。
 * 【概念】事件流是過程，訂單狀態是結果；單測鎖定寫入契約。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OrderEventServiceTest {

    @Mock
    private OrderEventRepository orderEventRepository;

    @InjectMocks
    private OrderEventService orderEventService;

    @Test
    void log_persistsEventFields() {
        orderEventService.log(11L, OrderEventType.REJECTED, "R001", "limit", "{\"x\":1}");

        ArgumentCaptor<OrderEventEntity> captor = ArgumentCaptor.forClass(OrderEventEntity.class);
        verify(orderEventRepository).save(captor.capture());
        OrderEventEntity saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(11L);
        assertThat(saved.getEvent()).isEqualTo(OrderEventType.REJECTED);
        assertThat(saved.getRiskRuleCode()).isEqualTo("R001");
        assertThat(saved.getRejectReason()).isEqualTo("limit");
        assertThat(saved.getPayloadJson()).isEqualTo("{\"x\":1}");
    }
}
