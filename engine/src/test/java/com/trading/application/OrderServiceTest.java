package com.trading.application;

import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【職責】保護 {@link OrderService} 建單、狀態標記與查詢。
 * 【技巧】Mockito 驗證寫入欄位與分頁分支。
 * 【概念】訂單持久化與風控編排分離，單測只驗資料怎麼存。
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createRejected_persistsRejectedFields() {
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEntity saved = orderService.createRejected("c1", "BTCUSDT", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("100"), "R001", "limit");

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(saved.getRiskRuleCode()).isEqualTo("R001");
        assertThat(saved.getRejectReason()).isEqualTo("limit");
    }

    @Test
    void createPending_persistsNewStatus() {
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEntity saved = orderService.createPending("c2", "ETHUSDT", OrderSide.SELL,
                new BigDecimal("2"), new BigDecimal("2000"));

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(saved.getSymbol()).isEqualTo("ETHUSDT");
        assertThat(saved.getSide()).isEqualTo(OrderSide.SELL);
    }

    @Test
    void markRejected_updatesExistingOrder() {
        OrderEntity order = new OrderEntity();
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEntity saved = orderService.markRejected(order, "R003", "bad qty");

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(saved.getRiskRuleCode()).isEqualTo("R003");
    }

    @Test
    void markFilled_setsFilledQuantityToOrderQty() {
        OrderEntity order = new OrderEntity();
        order.setQuantity(new BigDecimal("3"));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEntity saved = orderService.markFilled(order);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(saved.getFilledQuantity()).isEqualByComparingTo("3");
    }

    @Test
    void markPartiallyFilled_setsCumulativeQty() {
        OrderEntity order = new OrderEntity();
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEntity saved = orderService.markPartiallyFilled(order, new BigDecimal("0.4"));

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(saved.getFilledQuantity()).isEqualByComparingTo("0.4");
    }

    @Test
    void markCancelled_setsCancelled() {
        OrderEntity order = new OrderEntity();
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(orderService.markCancelled(order).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void getById_missing_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_found_returnsEntity() {
        OrderEntity order = new OrderEntity();
        order.setId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThat(orderService.getById(1L)).isSameAs(order);
    }

    @Test
    void list_symbolAndStatus_usesCombinedQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findBySymbolAndStatus("BTCUSDT", OrderStatus.FILLED, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<OrderEntity> page = orderService.list("BTCUSDT", OrderStatus.FILLED, pageable);

        assertThat(page.getContent()).isEmpty();
        verify(orderRepository).findBySymbolAndStatus("BTCUSDT", OrderStatus.FILLED, pageable);
    }

    @Test
    void list_symbolOnly_usesSymbolQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findBySymbol("ETHUSDT", pageable)).thenReturn(new PageImpl<>(List.of()));

        orderService.list("ETHUSDT", null, pageable);

        verify(orderRepository).findBySymbol("ETHUSDT", pageable);
    }

    @Test
    void list_statusOnly_usesStatusQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findByStatus(OrderStatus.NEW, pageable)).thenReturn(new PageImpl<>(List.of()));

        orderService.list(null, OrderStatus.NEW, pageable);

        verify(orderRepository).findByStatus(OrderStatus.NEW, pageable);
    }

    @Test
    void list_noFilter_usesFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        orderService.list(null, null, pageable);

        verify(orderRepository).findAll(pageable);
    }
}
