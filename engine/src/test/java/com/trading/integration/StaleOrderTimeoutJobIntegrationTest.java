package com.trading.integration;

import com.trading.application.StaleOrderCancellationService;
import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.repository.OrderEventRepository;
import com.trading.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class StaleOrderTimeoutJobIntegrationTest {

    @Autowired
    private StaleOrderCancellationService staleOrderCancellationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        orderEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private OrderEntity save(OrderStatus status) {
        OrderEntity o = new OrderEntity();
        o.setClientOrderId("coid-" + System.nanoTime());
        o.setSymbol("BTCUSDT");
        o.setSide(OrderSide.BUY);
        o.setQuantity(new BigDecimal("1"));
        o.setPrice(new BigDecimal("65000"));
        o.setStatus(status);
        return orderRepository.save(o);
    }

    private void ageOrder(Long id) {
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                OffsetDateTime.now().minusDays(2), id);
    }

    @Test
    void JOB_STALE_001_CANCEL_agedNewOrderIsCancelledWithEvent() {
        OrderEntity stale = save(OrderStatus.NEW);
        ageOrder(stale.getId());

        int cancelled = staleOrderCancellationService.cancelStaleOrders();

        assertThat(cancelled).isEqualTo(1);
        assertThat(orderRepository.findById(stale.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderEventRepository.findByOrderIdOrderByCreatedAtAsc(stale.getId()))
                .anyMatch(e -> e.getEvent().name().equals("CANCELLED"));
    }

    @Test
    void JOB_STALE_002_SKIP_RECENT_recentOrderNotCancelled() {
        OrderEntity recent = save(OrderStatus.NEW);

        int cancelled = staleOrderCancellationService.cancelStaleOrders();

        assertThat(cancelled).isZero();
        assertThat(orderRepository.findById(recent.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.NEW);
    }

    @Test
    void JOB_STALE_003_SKIP_TERMINAL_filledOrderNeverCancelled() {
        OrderEntity filled = save(OrderStatus.FILLED);
        ageOrder(filled.getId());

        int cancelled = staleOrderCancellationService.cancelStaleOrders();

        assertThat(cancelled).isZero();
        assertThat(orderRepository.findById(filled.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.FILLED);
    }
}
