package com.trading.application;

import com.trading.config.JobProperties;
import com.trading.domain.FailedCommandStatus;
import com.trading.infrastructure.repository.FailedCommandRepository;
import com.trading.infrastructure.repository.OrderEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 【職責】JOB-D 業務：依保留天數清理過期訂單事件與已終結失敗指令。
 * 【技巧】{@code @Transactional}；依 cutoff 呼叫 Repository 批次刪除；回傳 {@link CleanupResult} record。
 * 【概念】審計表會一直長；只刪「夠舊且已終結」的資料，保留近期可追查。
 * 【邊界】不決定 cron（Job 觸發）；不刪進行中的 PENDING 失敗指令。
 */
@Service
@Slf4j
public class DataCleanupService {

    private final OrderEventRepository orderEventRepository;
    private final FailedCommandRepository failedCommandRepository;
    private final JobProperties jobProperties;

    /** 建構子注入清理所需 Repository／設定。 */
    public DataCleanupService(OrderEventRepository orderEventRepository,
                              FailedCommandRepository failedCommandRepository,
                              JobProperties jobProperties) {
        this.orderEventRepository = orderEventRepository;
        this.failedCommandRepository = failedCommandRepository;
        this.jobProperties = jobProperties;
    }

    /**
     * 【職責】執行一次清理並回傳各表刪除筆數。
     * 【技巧】{@code now.minusDays(retention)} 算 cutoff；只刪 SUCCEEDED／DEAD 的失敗指令。
     * 【概念】事件與失敗指令可有不同保留期——事件偏稽核、失敗指令偏營運佇列。
     * @return 清理結果（事件刪除數、失敗指令刪除數）
     */
    @Transactional
    public CleanupResult cleanup() {
        JobProperties.Cleanup config = jobProperties.getCleanup();
        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime eventCutoff = now.minusDays(config.getEventRetentionDays());
        int deletedEvents = orderEventRepository.deleteByCreatedAtBefore(eventCutoff);

        OffsetDateTime commandCutoff = now.minusDays(config.getFailedCommandRetentionDays());
        int deletedCommands = failedCommandRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(FailedCommandStatus.SUCCEEDED, FailedCommandStatus.DEAD), commandCutoff);

        if (deletedEvents > 0 || deletedCommands > 0) {
            log.info("JOB-D cleanup removed {} order events and {} failed commands",
                    deletedEvents, deletedCommands);
        }
        return new CleanupResult(deletedEvents, deletedCommands);
    }

    /**
     * 【職責】承載一次清理的刪除統計。
     * 【技巧】Java record 自動產生 accessor／equals。
     * 【概念】用小型不可變結果物件回傳多個計數，比 Map 或陣列更可讀。
     */
    public record CleanupResult(int deletedOrderEvents, int deletedFailedCommands) {
    }
}
