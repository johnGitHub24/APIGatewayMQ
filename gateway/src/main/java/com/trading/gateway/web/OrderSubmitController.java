package com.trading.gateway.web;

import com.trading.common.OrderAcceptedResponse;
import com.trading.common.OrderCommandMessage;
import com.trading.gateway.config.GatewayProperties;
import com.trading.gateway.service.OrderCommandProducer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 【職責】薄 Controller：非同步下單 HTTP 入口（驗證 → 組命令 → 發 Kafka → 回 202 Accepted）。
 * 【技巧】WebFlux {@code Mono}；{@code @Valid} Bean Validation；{@code Idempotency-Key} 標頭；
 *         {@code Mono.fromFuture(producer.publish(...))} 銜接 Kafka CompletableFuture。
 * 【概念】202 表示「已受理入隊」，不是「已成交」。削峰靠 MQ：入口快回，Engine 慢慢消化。
 *         請求通常先經 {@link com.trading.gateway.filter.RateLimitWebFilter}。不負責風控／持久化／直接存取 Repository。
 * 【邊界】只組裝訊息與 HTTP 狀態；發送細節在 {@link OrderCommandProducer}；查結果走 pollUrl 或代理 Controller。
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderSubmitController {

    private final OrderCommandProducer producer;
    private final GatewayProperties properties;

    /**
     * 【職責】注入 Kafka 發送器與 Gateway 設定。
     * 【技巧】建構子注入；{@code instanceId} 來自 properties 寫入命令的 sourceGateway。
     * 【概念】Controller 不 new Producer，由 Spring 管理生命週期與連線。
     *
     * @param producer   將訂單命令寫入 Kafka
     * @param properties 提供 instanceId 等
     */
    public OrderSubmitController(OrderCommandProducer producer, GatewayProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    /**
     * 【職責】接受新訂單：驗證 body、解析冪等鍵、組裝命令並非同步發布，成功回 202。
     * 【技巧】{@link OrderCommandMessage#builder()}／{@link OrderAcceptedResponse#builder()}；
     *         失敗 {@code onErrorMap} 成 503 {@link ResponseStatusException}。
     * 【概念】冪等鍵優先於 body.clientOrderId，再退回伺服器 UUID——讓重試客戶端可穩定追蹤同一筆意圖。
     * 【邊界】不呼叫 Engine HTTP；Kafka 不可用時才變 503，不假裝已處理。
     *
     * @param request        JSON 訂單內容，需通過 Bean Validation
     * @param idempotencyKey 可選 {@code Idempotency-Key}，優先作為 clientOrderId
     * @return 202 與 {@link OrderAcceptedResponse}
     */
    @PostMapping
    public Mono<ResponseEntity<OrderAcceptedResponse>> submitOrder(
            @RequestBody @Valid GatewayOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        String clientOrderId = resolveClientOrderId(request, idempotencyKey);
        String commandId = UUID.randomUUID().toString();

        OrderCommandMessage command = OrderCommandMessage.builder()
                .commandId(commandId)
                .clientOrderId(clientOrderId)
                .symbol(request.getSymbol())
                .side(request.getSide())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .submittedAt(Instant.now())
                .sourceGateway(properties.getInstanceId())
                .build();

        OrderAcceptedResponse accepted = OrderAcceptedResponse.builder()
                .commandId(commandId)
                .clientOrderId(clientOrderId)
                .status("ACCEPTED")
                .message("Order queued for processing")
                .pollUrl("/api/v1/orders?clientOrderId=" + encode(clientOrderId))
                .acceptedAt(Instant.now())
                .build();

        return Mono.fromFuture(producer.publish(command).thenApply(result -> accepted))
                .map(body -> ResponseEntity.status(HttpStatus.ACCEPTED).body(body))
                .onErrorMap(ex -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Failed to queue order", ex));
    }

    /** 將 clientOrderId 做 URL 編碼，避免查詢字串特殊字元破壞 URL。 */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 決定客戶端訂單識別：Idempotency-Key &gt; body.clientOrderId &gt; 伺服器 UUID。
     */
    private String resolveClientOrderId(GatewayOrderRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        if (request.getClientOrderId() != null && !request.getClientOrderId().isBlank()) {
            return request.getClientOrderId();
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 【職責】Gateway 對外下單請求的 JSON 對應結構（請求 DTO）。
     * 【技巧】Jakarta {@code @NotBlank}／{@code @NotNull}；Lombok {@code @Data} 產生存取子供綁定與驗證。
     * 【概念】與 {@link OrderCommandMessage} 分離：HTTP 契約可演進，不必與 Kafka 訊息欄位一一鎖死
     *         （例如此處無 sourceGateway，由伺服器填入）。
     * 【邊界】僅承載輸入；不含受理回應欄位（見 {@link OrderAcceptedResponse}）。
     */
    @Data
    public static class GatewayOrderRequest {

        /** 客戶端自訂訂單編號；未提供時由冪等鍵或伺服器 UUID 決定。 */
        private String clientOrderId;

        /** 交易標的，例如 BTCUSDT。 */
        @NotBlank
        private String symbol;

        /** 買賣方向，例如 BUY 或 SELL。 */
        @NotBlank
        private String side;

        /** 委託數量。 */
        @NotNull
        private BigDecimal quantity;

        /** 委託價格。 */
        @NotNull
        private BigDecimal price;
    }
}
