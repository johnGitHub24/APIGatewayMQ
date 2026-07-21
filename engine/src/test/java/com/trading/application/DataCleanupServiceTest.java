package com.trading.application;

import com.trading.config.JobProperties;
import com.trading.domain.FailedCommandStatus;
import com.trading.infrastructure.repository.FailedCommandRepository;
import com.trading.infrastructure.repository.OrderEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataCleanupServiceTest {

    @Mock
    private OrderEventRepository orderEventRepository;

    @Mock
    private FailedCommandRepository failedCommandRepository;

    private final JobProperties jobProperties = new JobProperties();

    private DataCleanupService service;

    @BeforeEach
    void setUp() {
        service = new DataCleanupService(orderEventRepository, failedCommandRepository, jobProperties);
    }

    @Test
    void JOB_CLEAN_001_EVENTS_deletesExpiredEventsAndCommands() {
        when(orderEventRepository.deleteByCreatedAtBefore(any())).thenReturn(7);
        when(failedCommandRepository.deleteByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(3);

        DataCleanupService.CleanupResult result = service.cleanup();

        assertThat(result.deletedOrderEvents()).isEqualTo(7);
        assertThat(result.deletedFailedCommands()).isEqualTo(3);
    }

    @Test
    void JOB_CLEAN_002_onlyPurgesTerminalFailedCommands() {
        when(orderEventRepository.deleteByCreatedAtBefore(any())).thenReturn(0);
        ArgumentCaptor<Collection<FailedCommandStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        when(failedCommandRepository.deleteByStatusInAndUpdatedAtBefore(captor.capture(), any(OffsetDateTime.class)))
                .thenReturn(0);

        service.cleanup();

        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(FailedCommandStatus.SUCCEEDED, FailedCommandStatus.DEAD);
    }
}
