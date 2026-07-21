package com.trading.gateway.service;

import com.trading.common.OrderCommandMessage;
import com.trading.common.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 【職責】將已組裝的 {@link OrderCommandMessage} 非同步寫入 Kafka（下單削峰的發送層）。
 * 【技巧】{@link KafkaTemplate#send}；以 {@code symbol} 當 partition key；回傳 {@link CompletableFuture}（內含 {@link SendResult}）。
 * 【概念】Gateway 不「做完訂單」才回覆，而是「入隊成功」就回 202——這是 MQ 削峰的核心：
 *         入口快速 ACK，重活交給 Engine Consumer Group 消化。
 * 【邊界】不驗證價格／風控、不寫 DB；只負責可靠送件。組裝訊息由 Controller，消費由 Engine。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCommandProducer {

    /** 將訊息序列化並送到指定 topic。 */
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 【職責】發布一筆訂單命令到 {@link Topics#ORDER_COMMANDS}。
     * 【技巧】{@code kafkaTemplate.send(topic, key, value)}；key={@code symbol} 讓同標的盡量同 partition，保留相對順序。
     * 【概念】Partition key 影響順序與負載：同 key 有序，不同 key 可並行。用 symbol 是常見交易系統取捨。
     *
     * @param command 已組裝好的跨服務訂單命令
     * @return 送件結果的 Future，可用於判斷 broker 是否接受
     */
    public CompletableFuture<SendResult<String, Object>> publish(OrderCommandMessage command) {
        log.info("Publishing order command {} symbol={}", command.getCommandId(), command.getSymbol());
        return kafkaTemplate.send(Topics.ORDER_COMMANDS, command.getSymbol(), command);
    }
}
