package com.trading.application;

import com.trading.infrastructure.entity.PnlSnapshotEntity;
import com.trading.infrastructure.entity.PositionEntity;
import com.trading.infrastructure.repository.PnlSnapshotRepository;
import com.trading.infrastructure.repository.PositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PnlSnapshotServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private PnlSnapshotRepository pnlSnapshotRepository;

    @InjectMocks
    private PnlSnapshotService service;

    private PositionEntity position(String symbol, String qty, String pnl) {
        PositionEntity p = new PositionEntity();
        p.setSymbol(symbol);
        p.setQuantity(new BigDecimal(qty));
        p.setAvgPrice(new BigDecimal("65000"));
        p.setMarkPrice(new BigDecimal("66000"));
        p.setUnrealizedPnl(new BigDecimal(pnl));
        return p;
    }

    @Test
    void JOB_PNL_001_SNAPSHOT_writesRowPerPosition() {
        when(positionRepository.findAll()).thenReturn(List.of(position("BTCUSDT", "0.5", "500")));
        when(pnlSnapshotRepository.findBySnapshotDateAndSymbol(any(LocalDate.class), eq("BTCUSDT")))
                .thenReturn(List.of());

        int written = service.captureSnapshot();

        assertThat(written).isEqualTo(1);
        ArgumentCaptor<PnlSnapshotEntity> captor = ArgumentCaptor.forClass(PnlSnapshotEntity.class);
        verify(pnlSnapshotRepository).save(captor.capture());
        PnlSnapshotEntity saved = captor.getValue();
        assertThat(saved.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(saved.getUnrealizedPnl()).isEqualByComparingTo("500");
        assertThat(saved.getSnapshotDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void JOB_PNL_002_EMPTY_noPositions_writesNothing() {
        when(positionRepository.findAll()).thenReturn(List.of());

        int written = service.captureSnapshot();

        assertThat(written).isZero();
        verify(pnlSnapshotRepository, never()).save(any());
    }

    @Test
    void JOB_PNL_003_IDEMPOTENT_skipsSymbolAlreadySnapshottedToday() {
        when(positionRepository.findAll()).thenReturn(List.of(position("BTCUSDT", "0.5", "500")));
        when(pnlSnapshotRepository.findBySnapshotDateAndSymbol(any(LocalDate.class), eq("BTCUSDT")))
                .thenReturn(List.of(new PnlSnapshotEntity()));

        int written = service.captureSnapshot();

        assertThat(written).isZero();
        verify(pnlSnapshotRepository, never()).save(any());
    }
}
