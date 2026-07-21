package com.trading.application;

import com.trading.infrastructure.entity.PnlSnapshotEntity;
import com.trading.infrastructure.entity.PositionEntity;
import com.trading.infrastructure.repository.PnlSnapshotRepository;
import com.trading.infrastructure.repository.PositionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 【職責】JOB-B：為每個持倉建立當日 PnL／持倉結算快照，並支援依日查詢。
 * 【技巧】同日同 symbol 先查再寫（冪等）；null 數值以 ZERO 落地。
 * 【概念】快照＝「那天收盤時倉長怎樣」；與即時持倉表分離，避免歷史被後續成交改寫。
 * 【邊界】不觸發 cron；不計算新的 mark（沿用持倉當下欄位）。
 */
@Service
@Slf4j
public class PnlSnapshotService {

    private final PositionRepository positionRepository;
    private final PnlSnapshotRepository pnlSnapshotRepository;

    /** 建構子注入快照持久化依賴。 */
    public PnlSnapshotService(PositionRepository positionRepository,
                              PnlSnapshotRepository pnlSnapshotRepository) {
        this.positionRepository = positionRepository;
        this.pnlSnapshotRepository = pnlSnapshotRepository;
    }

    /**
     * 【職責】建立當日結算快照；已存在的 symbol 跳過。
     * 【技巧】{@code findBySnapshotDateAndSymbol} 判斷冪等；迴圈寫入。
     * 【概念】Job 重跑安全：不會因重試產生重複結算列。
     * @return 本次新寫入的快照筆數
     */
    @Transactional
    public int captureSnapshot() {
        LocalDate today = LocalDate.now();
        List<PositionEntity> positions = positionRepository.findAll();
        int written = 0;

        for (PositionEntity position : positions) {
            boolean alreadyCaptured = !pnlSnapshotRepository
                    .findBySnapshotDateAndSymbol(today, position.getSymbol()).isEmpty();
            if (alreadyCaptured) {
                continue;
            }

            PnlSnapshotEntity snapshot = new PnlSnapshotEntity();
            snapshot.setSnapshotDate(today);
            snapshot.setSymbol(position.getSymbol());
            snapshot.setQuantity(nullSafe(position.getQuantity()));
            snapshot.setAvgPrice(nullSafe(position.getAvgPrice()));
            snapshot.setMarkPrice(nullSafe(position.getMarkPrice()));
            snapshot.setUnrealizedPnl(nullSafe(position.getUnrealizedPnl()));
            pnlSnapshotRepository.save(snapshot);
            written++;
        }

        if (written > 0) {
            log.info("JOB-B captured {} PnL snapshots for {}", written, today);
        }
        return written;
    }

    /**
     * 【職責】依結算日查詢 PnL 快照。
     * 【技巧】{@code date == null} 時用 {@link LocalDate#now()}。
     * 【概念】API 省略日期＝「看今天」，減少呼叫端樣板碼。
     * @param date 結算日；null 表示今日
     * @return 該日快照列表
     */
    @Transactional(readOnly = true)
    public List<PnlSnapshotEntity> findByDate(LocalDate date) {
        return pnlSnapshotRepository.findBySnapshotDate(date != null ? date : LocalDate.now());
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
