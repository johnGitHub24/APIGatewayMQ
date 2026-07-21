package com.trading.application;

import com.trading.common.OrderCommandMessage;
import com.trading.config.JobProperties;
import com.trading.domain.FailedCommandStatus;
import com.trading.domain.OrderSide;
import com.trading.dto.CreateOrderRequest;
import com.trading.infrastructure.entity.FailedCommandEntity;
import com.trading.infrastructure.repository.FailedCommandRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 【職責】JOB-C：失敗下單指令的持久化 DLQ 寫入、定時重試與查詢。
 * 【技巧】{@code Propagation.REQUIRES_NEW} 獨立交易寫失敗列；批次掃描 PENDING + {@code nextRetryAt}；指數式 backoff。
 * 【概念】Kafka 消費當下若 DB 掛掉，訊息可能已 commit；把指令存表才能「稍後再試」而不丟單。
 * 【邊界】業務拒單視為成功結案不重試；達 maxAttempts 標 DEAD。不負責 cron 觸發。
 */
@Service
@Slf4j
public class FailedCommandService {

    private final FailedCommandRepository failedCommandRepository;
    private final TradingService tradingService;
    private final JobProperties jobProperties;

    /** 建構子注入失敗指令持久化與重試協作依賴。 */
    public FailedCommandService(FailedCommandRepository failedCommandRepository,
                                TradingService tradingService,
                                JobProperties jobProperties) {
        this.failedCommandRepository = failedCommandRepository;
        this.tradingService = tradingService;
        this.jobProperties = jobProperties;
    }

    /**
     * 【職責】記錄一筆失敗指令供後續重試。
     * 【技巧】{@code REQUIRES_NEW}：即使外層交易 rollback，此列仍提交。
     * 【概念】Consumer 捕捉基礎設施例外後呼叫；reason 截斷避免欄位過長。
     * @param command 原始 Kafka 指令
     * @param reason  失敗原因摘要
     * @return 已持久化的失敗指令實體
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailedCommandEntity recordFailure(OrderCommandMessage command, String reason) {
        FailedCommandEntity entity = new FailedCommandEntity();
        entity.setCommandId(command.getCommandId());
        entity.setClientOrderId(command.getClientOrderId());
        entity.setSymbol(command.getSymbol());
        entity.setSide(OrderSide.valueOf(command.getSide()));
        entity.setQuantity(command.getQuantity());
        entity.setPrice(command.getPrice());
        entity.setFailureReason(truncate(reason));
        entity.setAttempts(0);
        entity.setStatus(FailedCommandStatus.PENDING);
        entity.setNextRetryAt(OffsetDateTime.now());
        log.warn("JOB-C recorded failed command commandId={} symbol={} reason={}",
                command.getCommandId(), command.getSymbol(), reason);
        return failedCommandRepository.save(entity);
    }

    /**
     * 【職責】掃描到期的失敗指令並重試下單。
     * 【技巧】{@link PageRequest} 限批次；成功／風控拒 → SUCCEEDED；其他失敗則加 attempts 與 nextRetryAt。
     * 【概念】風控拒絕代表「已正確處理」，標成功避免無限重試；基礎設施錯誤才繼續 backoff。
     * @return 本次成功重投的筆數（不含風控拒但標 SUCCEEDED 的語意計數以程式為準）
     */
    @Transactional
    public int retryFailedCommands() {
        JobProperties.Retry config = jobProperties.getRetry();
        OffsetDateTime now = OffsetDateTime.now();

        List<FailedCommandEntity> due = failedCommandRepository.findByStatusAndNextRetryAtLessThanEqual(
                FailedCommandStatus.PENDING, now, PageRequest.of(0, config.getBatchSize()));

        int succeeded = 0;
        for (FailedCommandEntity entity : due) {
            entity.setAttempts(entity.getAttempts() + 1);
            try {
                tradingService.placeOrder(toRequest(entity), entity.getClientOrderId());
                entity.setStatus(FailedCommandStatus.SUCCEEDED);
                entity.setFailureReason(null);
                succeeded++;
            } catch (RiskRejectedException ex) {
                // 業務性拒絕代表訊息已被處理，不需再重試。
                entity.setStatus(FailedCommandStatus.SUCCEEDED);
                entity.setFailureReason("Risk rejected: " + ex.getErrorCode());
            } catch (Exception ex) {
                entity.setFailureReason(truncate(ex.getMessage()));
                if (entity.getAttempts() >= config.getMaxAttempts()) {
                    entity.setStatus(FailedCommandStatus.DEAD);
                    log.error("JOB-C command commandId={} moved to DEAD after {} attempts",
                            entity.getCommandId(), entity.getAttempts());
                } else {
                    entity.setNextRetryAt(now.plusSeconds(config.getBackoffSeconds() * entity.getAttempts()));
                }
            }
            failedCommandRepository.save(entity);
        }

        if (!due.isEmpty()) {
            log.info("JOB-C processed {} failed commands, {} succeeded", due.size(), succeeded);
        }
        return succeeded;
    }

    /**
     * 【職責】依狀態查詢失敗指令（有上限，避免無界查詢）。
     * 【技巧】{@code @Transactional(readOnly = true)}；null status 時取最近一頁全部。
     * 【概念】維運 API 需要「看佇列」；永遠帶 limit，避免一次撈爆。
     * @param status 可選；{@code null} 時回傳最近一頁全部
     * @return 失敗指令列表
     */
    @Transactional(readOnly = true)
    public List<FailedCommandEntity> findByStatus(FailedCommandStatus status) {
        int limit = Math.max(jobProperties.getRetry().getBatchSize(), 100);
        if (status == null) {
            return failedCommandRepository.findAll(PageRequest.of(0, limit)).getContent();
        }
        return failedCommandRepository.findByStatus(status, PageRequest.of(0, limit));
    }

    private CreateOrderRequest toRequest(FailedCommandEntity entity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setClientOrderId(entity.getClientOrderId());
        request.setSymbol(entity.getSymbol());
        request.setSide(entity.getSide());
        request.setQuantity(entity.getQuantity());
        request.setPrice(entity.getPrice());
        return request;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 512 ? value.substring(0, 512) : value;
    }
}
