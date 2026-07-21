package com.trading.integration;

import com.trading.application.DataCleanupService;
import com.trading.domain.FailedCommandStatus;
import com.trading.domain.OrderEventType;
import com.trading.domain.OrderSide;
import com.trading.infrastructure.entity.FailedCommandEntity;
import com.trading.infrastructure.entity.OrderEventEntity;
import com.trading.infrastructure.repository.FailedCommandRepository;
import com.trading.infrastructure.repository.OrderEventRepository;
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
class DataCleanupJobIntegrationTest {

    @Autowired
    private DataCleanupService dataCleanupService;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Autowired
    private FailedCommandRepository failedCommandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        orderEventRepository.deleteAll();
        failedCommandRepository.deleteAll();
    }

    private OrderEventEntity saveEvent(Long orderId) {
        OrderEventEntity e = new OrderEventEntity();
        e.setOrderId(orderId);
        e.setEvent(OrderEventType.RECEIVED);
        return orderEventRepository.save(e);
    }

    private FailedCommandEntity saveCommand(FailedCommandStatus status) {
        FailedCommandEntity e = new FailedCommandEntity();
        e.setSymbol("BTCUSDT");
        e.setSide(OrderSide.BUY);
        e.setQuantity(new BigDecimal("0.5"));
        e.setPrice(new BigDecimal("65000"));
        e.setStatus(status);
        e.setNextRetryAt(OffsetDateTime.now());
        return failedCommandRepository.save(e);
    }

    @Test
    void JOB_CLEAN_001_EVENTS_deletesExpiredEventsKeepsRecent() {
        OrderEventEntity old = saveEvent(1L);
        OrderEventEntity recent = saveEvent(2L);
        jdbcTemplate.update("UPDATE order_events SET created_at = ? WHERE id = ?",
                OffsetDateTime.now().minusDays(31), old.getId());

        DataCleanupService.CleanupResult result = dataCleanupService.cleanup();

        assertThat(result.deletedOrderEvents()).isEqualTo(1);
        assertThat(orderEventRepository.findById(old.getId())).isEmpty();
        assertThat(orderEventRepository.findById(recent.getId())).isPresent();
    }

    @Test
    void JOB_CLEAN_003_FAILED_COMMANDS_purgesTerminalOldKeepsPending() {
        FailedCommandEntity dead = saveCommand(FailedCommandStatus.DEAD);
        FailedCommandEntity succeeded = saveCommand(FailedCommandStatus.SUCCEEDED);
        FailedCommandEntity pending = saveCommand(FailedCommandStatus.PENDING);
        jdbcTemplate.update("UPDATE failed_commands SET updated_at = ? WHERE id IN (?, ?, ?)",
                OffsetDateTime.now().minusDays(8), dead.getId(), succeeded.getId(), pending.getId());

        DataCleanupService.CleanupResult result = dataCleanupService.cleanup();

        assertThat(result.deletedFailedCommands()).isEqualTo(2);
        assertThat(failedCommandRepository.findById(dead.getId())).isEmpty();
        assertThat(failedCommandRepository.findById(succeeded.getId())).isEmpty();
        assertThat(failedCommandRepository.findById(pending.getId())).isPresent();
    }
}
