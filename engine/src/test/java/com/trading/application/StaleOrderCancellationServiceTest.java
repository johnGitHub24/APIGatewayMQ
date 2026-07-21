package com.trading.application;

import com.trading.config.JobProperties;
import com.trading.domain.OrderEventType;
import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleOrderCancellationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventService orderEventService;

    private final JobProperties jobProperties = new JobProperties();

    private StaleOrderCancellationService service;

    @BeforeEach
    void setUp() {
        service = new StaleOrderCancellationService(orderRepository, orderEventService, jobProperties);
    }

    private OrderEntity order(long id, OrderStatus status) {
        OrderEntity o = new OrderEntity();
        o.setId(id);
        o.setSymbol("BTCUSDT");
        o.setSide(OrderSide.BUY);
        o.setQuantity(new BigDecimal("1"));
        o.setPrice(new BigDecimal("65000"));
        o.setStatus(status);
        return o;
    }

    @Test
    void JOB_STALE_001_CANCEL_marksStaleOrdersCancelledAndLogsEvent() {
        OrderEntity stale = order(1L, OrderStatus.NEW);
        when(orderRepository.findByStatusInAndCreatedAtBefore(any(Collection.class), any(), any()))
                .thenReturn(List.of(stale));

        int cancelled = service.cancelStaleOrders();

        assertThat(cancelled).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stale.getRejectReason()).contains("timed out");
        verify(orderRepository).save(stale);
        verify(orderEventService).log(eq(1L), eq(OrderEventType.CANCELLED), isNull(), any(), isNull());
    }

    @Test
    void JOB_STALE_002_EMPTY_noStaleOrders_returnsZero() {
        when(orderRepository.findByStatusInAndCreatedAtBefore(any(Collection.class), any(), any()))
                .thenReturn(List.of());

        int cancelled = service.cancelStaleOrders();

        assertThat(cancelled).isZero();
        verify(orderRepository, never()).save(any());
        verify(orderEventService, never()).log(anyLong(), any(), any(), any(), any());
    }

    @Test
    void JOB_STALE_003_MULTI_cancelsAllReturnedOrders() {
        when(orderRepository.findByStatusInAndCreatedAtBefore(any(Collection.class), any(), any()))
                .thenReturn(List.of(order(1L, OrderStatus.NEW), order(2L, OrderStatus.PARTIALLY_FILLED)));

        int cancelled = service.cancelStaleOrders();

        assertThat(cancelled).isEqualTo(2);
        verify(orderRepository, times(2)).save(any(OrderEntity.class));
    }

    @Test
    void JOB_STALE_004_onlyScansCancellableStatuses() {
        ArgumentCaptor<Collection<OrderStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        when(orderRepository.findByStatusInAndCreatedAtBefore(captor.capture(), any(), any()))
                .thenReturn(List.of());

        service.cancelStaleOrders();

        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED);
    }
}
