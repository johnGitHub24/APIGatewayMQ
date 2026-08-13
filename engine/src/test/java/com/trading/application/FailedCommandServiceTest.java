package com.trading.application;

import com.trading.common.OrderCommandMessage;
import com.trading.config.JobProperties;
import com.trading.domain.FailedCommandStatus;
import com.trading.domain.OrderSide;
import com.trading.infrastructure.entity.FailedCommandEntity;
import com.trading.infrastructure.repository.FailedCommandRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailedCommandServiceTest {

    @Mock
    private FailedCommandRepository failedCommandRepository;

    @Mock
    private TradingService tradingService;

    private final JobProperties jobProperties = new JobProperties();

    private FailedCommandService service;

    @BeforeEach
    void setUp() {
        service = new FailedCommandService(failedCommandRepository, tradingService, jobProperties);
    }

    private FailedCommandEntity pending(int attempts) {
        FailedCommandEntity e = new FailedCommandEntity();
        e.setCommandId("cmd-1");
        e.setClientOrderId("coid-1");
        e.setSymbol("BTCUSDT");
        e.setSide(OrderSide.BUY);
        e.setQuantity(new BigDecimal("0.5"));
        e.setPrice(new BigDecimal("65000"));
        e.setAttempts(attempts);
        e.setStatus(FailedCommandStatus.PENDING);
        e.setNextRetryAt(OffsetDateTime.now().minusSeconds(1));
        return e;
    }

    @Test
    void recordFailure_persistsPendingCommand() {
        OrderCommandMessage command = OrderCommandMessage.builder()
                .commandId("cmd-rec")
                .clientOrderId("coid-rec")
                .symbol("BTCUSDT")
                .side("BUY")
                .quantity(new BigDecimal("0.5"))
                .price(new BigDecimal("65000"))
                .build();
        when(failedCommandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FailedCommandEntity saved = service.recordFailure(command, "kafka timeout");

        assertThat(saved.getCommandId()).isEqualTo("cmd-rec");
        assertThat(saved.getStatus()).isEqualTo(FailedCommandStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getFailureReason()).isEqualTo("kafka timeout");
        verify(failedCommandRepository).save(saved);
    }

    @Test
    void findByStatus_null_returnsRecentPage() {
        FailedCommandEntity entity = pending(0);
        when(failedCommandRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        List<FailedCommandEntity> found = service.findByStatus(null);

        assertThat(found).containsExactly(entity);
    }

    @Test
    void findByStatus_pending_filtersRepository() {
        FailedCommandEntity entity = pending(0);
        when(failedCommandRepository.findByStatus(eq(FailedCommandStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(entity));

        List<FailedCommandEntity> found = service.findByStatus(FailedCommandStatus.PENDING);

        assertThat(found).containsExactly(entity);
    }

    @Test
    void JOB_RETRY_001_SUCCESS_replaysAndMarksSucceeded() {
        FailedCommandEntity entity = pending(0);
        when(failedCommandRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(FailedCommandStatus.PENDING), any(), any())).thenReturn(List.of(entity));

        int succeeded = service.retryFailedCommands();

        assertThat(succeeded).isEqualTo(1);
        assertThat(entity.getStatus()).isEqualTo(FailedCommandStatus.SUCCEEDED);
        assertThat(entity.getAttempts()).isEqualTo(1);
        verify(tradingService).placeOrder(any(), eq("coid-1"));
        verify(failedCommandRepository).save(entity);
    }

    @Test
    void JOB_RETRY_002_BACKOFF_transientFailure_staysPendingAndReschedules() {
        FailedCommandEntity entity = pending(0);
        when(failedCommandRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(FailedCommandStatus.PENDING), any(), any())).thenReturn(List.of(entity));
        doThrow(new RuntimeException("db down")).when(tradingService).placeOrder(any(), any());

        int succeeded = service.retryFailedCommands();

        assertThat(succeeded).isZero();
        assertThat(entity.getStatus()).isEqualTo(FailedCommandStatus.PENDING);
        assertThat(entity.getAttempts()).isEqualTo(1);
        assertThat(entity.getNextRetryAt()).isAfter(OffsetDateTime.now());
        assertThat(entity.getFailureReason()).contains("db down");
    }

    @Test
    void JOB_RETRY_003_DEAD_exceedsMaxAttempts_movesToDead() {
        FailedCommandEntity entity = pending(2); // maxAttempts default 3, next attempt = 3
        when(failedCommandRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(FailedCommandStatus.PENDING), any(), any())).thenReturn(List.of(entity));
        doThrow(new RuntimeException("still failing")).when(tradingService).placeOrder(any(), any());

        int succeeded = service.retryFailedCommands();

        assertThat(succeeded).isZero();
        assertThat(entity.getAttempts()).isEqualTo(3);
        assertThat(entity.getStatus()).isEqualTo(FailedCommandStatus.DEAD);
    }

    @Test
    void JOB_RETRY_004_RISK_REJECT_terminalNoRetry() {
        FailedCommandEntity entity = pending(0);
        when(failedCommandRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(FailedCommandStatus.PENDING), any(), any())).thenReturn(List.of(entity));
        doThrow(new RiskRejectedException("RISK_POSITION_LIMIT", "R001", "limit"))
                .when(tradingService).placeOrder(any(), any());

        int succeeded = service.retryFailedCommands();

        assertThat(succeeded).isZero();
        assertThat(entity.getStatus()).isEqualTo(FailedCommandStatus.SUCCEEDED);
        assertThat(entity.getFailureReason()).contains("RISK_POSITION_LIMIT");
    }
}
