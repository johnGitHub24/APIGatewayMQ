package com.trading.engine.messaging;

import com.trading.application.FailedCommandService;
import com.trading.application.RiskRejectedException;
import com.trading.application.TradingService;
import com.trading.common.OrderCommandMessage;
import com.trading.common.Topics;
import com.trading.domain.OrderSide;
import com.trading.dto.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 【職責】Kafka 下單指令消費者：把 MQ 訊息轉交 {@link TradingService} 執行。
 * 【技巧】{@code @KafkaListener} + {@code containerFactory}；{@code @Payload}/{@code @Header} 取本體與 partition／offset。
 * 【概念】Gateway 發到 {@link Topics#ORDER_COMMANDS} 後快速回 202；真正風控與落庫在此消化——這是削峰的「後端」。
 * 【邊界】不組裝 HTTP 回應；業務拒單不進 DLQ，基礎設施錯誤才寫 {@link FailedCommandService}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCommandConsumer {

    // 真正執行下單邏輯的應用服務（風控、狀態流轉、持久化都在這層）。
    private final TradingService tradingService;
    // 非業務錯誤會先記錄到 failed_commands，後續由重試 Job 處理。
    private final FailedCommandService failedCommandService;

    /**
     * 【職責】消費一筆下單指令並委派業務處理；失敗時分流業務拒單 vs 基礎設施錯誤。
     * 【技巧】MQ DTO → {@link CreateOrderRequest}；{@code try/catch} 區分 {@link RiskRejectedException} 與一般 Exception。
     * 【概念】風控拒絕＝「訊息已正確處理完（結果是拒）」；DB 掛掉＝「還沒處理好，應進 DLQ 稍後重試」。
     *         兩者若不區分，會把合法拒單反覆重試，造成噪音與重複拒單紀錄。
     * @param command   Kafka 訊息本體
     * @param partition 來源 partition（日誌用）
     * @param offset    來源 offset（日誌用）
     */
    @KafkaListener(
            topics = Topics.ORDER_COMMANDS,
            groupId = "${spring.kafka.consumer.group-id:trading-engine}",
            containerFactory = "orderCommandKafkaListenerContainerFactory")
    public void consume(@Payload OrderCommandMessage command,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Engine consuming commandId={} symbol={} partition={} offset={}",
                command.getCommandId(), command.getSymbol(), partition, offset);

        // MQ 訊息 DTO 轉成內部用 CreateOrderRequest，隔離外部契約與內部模型。
        CreateOrderRequest request = new CreateOrderRequest();
        request.setClientOrderId(command.getClientOrderId());
        request.setSymbol(command.getSymbol());
        request.setSide(OrderSide.valueOf(command.getSide()));
        request.setQuantity(command.getQuantity());
        request.setPrice(command.getPrice());

        try {
            tradingService.placeOrder(request, command.getClientOrderId());
            log.info("Engine processed commandId={} successfully", command.getCommandId());
        } catch (RiskRejectedException ex) {
            // 業務拒單（例如風控擋單）視為已處理，不進入基礎設施重試。
            log.warn("Engine rejected commandId={} errorCode={} rule={}",
                    command.getCommandId(), ex.getErrorCode(), ex.getRuleCode());
        } catch (Exception ex) {
            // 非業務性錯誤（DB/基礎設施）寫入持久化 DLQ，交由 JOB-C 重試。
            log.error("Engine failed to process commandId={}, persisting to DLQ", command.getCommandId(), ex);
            failedCommandService.recordFailure(command, ex.getMessage());
        }
    }
}
