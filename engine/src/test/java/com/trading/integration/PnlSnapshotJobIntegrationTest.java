package com.trading.integration;

import com.trading.application.PnlSnapshotService;
import com.trading.infrastructure.entity.PositionEntity;
import com.trading.infrastructure.repository.PnlSnapshotRepository;
import com.trading.infrastructure.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class PnlSnapshotJobIntegrationTest {

    @Autowired
    private PnlSnapshotService pnlSnapshotService;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private PnlSnapshotRepository pnlSnapshotRepository;

    @BeforeEach
    void clean() {
        pnlSnapshotRepository.deleteAll();
        positionRepository.deleteAll();
    }

    private void savePosition(String symbol, String qty, String pnl) {
        PositionEntity p = new PositionEntity();
        p.setSymbol(symbol);
        p.setQuantity(new BigDecimal(qty));
        p.setAvgPrice(new BigDecimal("65000"));
        p.setMarkPrice(new BigDecimal("66000"));
        p.setUnrealizedPnl(new BigDecimal(pnl));
        positionRepository.save(p);
    }

    @Test
    void JOB_PNL_001_SNAPSHOT_writesOneRowPerPosition() {
        savePosition("BTCUSDT", "0.5", "500");
        savePosition("ETHUSDT", "2", "-100");

        int written = pnlSnapshotService.captureSnapshot();

        assertThat(written).isEqualTo(2);
        assertThat(pnlSnapshotRepository.findBySnapshotDate(LocalDate.now())).hasSize(2);
    }

    @Test
    void JOB_PNL_002_EMPTY_noPositions_writesNothing() {
        int written = pnlSnapshotService.captureSnapshot();

        assertThat(written).isZero();
        assertThat(pnlSnapshotRepository.count()).isZero();
    }

    @Test
    void JOB_PNL_003_IDEMPOTENT_rerunSameDayDoesNotDuplicate() {
        savePosition("BTCUSDT", "0.5", "500");

        assertThat(pnlSnapshotService.captureSnapshot()).isEqualTo(1);
        assertThat(pnlSnapshotService.captureSnapshot()).isZero();
        assertThat(pnlSnapshotRepository.findBySnapshotDate(LocalDate.now())).hasSize(1);
    }
}
