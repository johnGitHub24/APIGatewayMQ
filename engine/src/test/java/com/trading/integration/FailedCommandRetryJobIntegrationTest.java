package com.trading.integration;

import com.trading.application.FailedCommandService;
import com.trading.domain.FailedCommandStatus;
import com.trading.domain.OrderSide;
import com.trading.infrastructure.entity.FailedCommandEntity;
import com.trading.infrastructure.repository.FailedCommandRepository;
import com.trading.infrastructure.repository.OrderEventRepository;
import com.trading.infrastructure.repository.OrderRepository;
import com.trading.infrastructure.repository.PositionRepository;
import com.trading.infrastructure.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class FailedCommandRetryJobIntegrationTest {

    @Autowired
    private FailedCommandService failedCommandService;

    @Autowired
    private FailedCommandRepository failedCommandRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @BeforeEach
    void clean() {
        failedCommandRepository.deleteAll();
        orderEventRepository.deleteAll();
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
    }

    private FailedCommandEntity saveFailed(String clientOrderId, OffsetDateTime nextRetryAt) {
        FailedCommandEntity e = new FailedCommandEntity();
        e.setCommandId("cmd-" + System.nanoTime());
        e.setClientOrderId(clientOrderId);
        e.setSymbol("BTCUSDT");
        e.setSide(OrderSide.BUY);
        e.setQuantity(new BigDecimal("0.5"));
        e.setPrice(new BigDecimal("65000"));
        e.setFailureReason("simulated infra failure");
        e.setAttempts(0);
        e.setStatus(FailedCommandStatus.PENDING);
        e.setNextRetryAt(nextRetryAt);
        return failedCommandRepository.save(e);
    }

    @Test
    void JOB_RETRY_001_SUCCESS_dueCommandIsReplayedAndOrderCreated() {
        FailedCommandEntity failed = saveFailed("retry-success-1", OffsetDateTime.now().minusSeconds(5));

        int succeeded = failedCommandService.retryFailedCommands();

        assertThat(succeeded).isEqualTo(1);
        assertThat(failedCommandRepository.findById(failed.getId()).orElseThrow().getStatus())
                .isEqualTo(FailedCommandStatus.SUCCEEDED);
        assertThat(orderRepository.findByClientOrderId("retry-success-1")).isPresent();
    }

    @Test
    void JOB_RETRY_004_SKIP_FUTURE_notDueCommandIsSkipped() {
        FailedCommandEntity future = saveFailed("retry-future-1", OffsetDateTime.now().plusMinutes(10));

        int succeeded = failedCommandService.retryFailedCommands();

        assertThat(succeeded).isZero();
        assertThat(failedCommandRepository.findById(future.getId()).orElseThrow().getStatus())
                .isEqualTo(FailedCommandStatus.PENDING);
        assertThat(orderRepository.findByClientOrderId("retry-future-1")).isEmpty();
    }
}
