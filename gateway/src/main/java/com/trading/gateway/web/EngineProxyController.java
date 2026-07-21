package com.trading.gateway.web;

import com.trading.gateway.service.EngineProxyService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 【職責】薄 Controller：將查詢／成交／取消等 HTTP API 透明轉發至 Engine（同步代理路徑）。
 * 【技巧】WebFlux {@code @RestController}；{@link ServerWebExchange} 取 path／headers／body；
 *         {@link DataBufferUtils#join} 聚合請求 body 後委派 {@link EngineProxyService#forward}。
 * 【概念】與 {@link OrderSubmitController} 分離：需要「立刻拿到 Engine 回應」走代理；
 *         新單寫入走 Kafka 202。Controller 只做協議適配，不寫商業規則、不直接碰 Repository。
 * 【邊界】不解析訂單 JSON、不做風控；路徑與方法對應由 mapping 宣告，轉發細節在 Service。
 */
@RestController
public class EngineProxyController {

    private final EngineProxyService proxyService;

    /**
     * 【職責】注入轉發服務。
     * 【技巧】建構子注入，利於單元測試替換。
     * 【概念】Controller 依賴 Service 介面／實作，不自己建立 WebClient。
     *
     * @param proxyService 執行實際 HTTP 轉發
     */
    public EngineProxyController(EngineProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * 【職責】代理所有 {@code GET /api/v1/**}（訂單查詢等讀取 API）。
     * 【技巧】{@code @GetMapping("/api/v1/**")} 萬用字元路徑；回傳 {@code Mono<ResponseEntity<byte[]>>}。
     * 【概念】讀取走同步代理，客戶端可直接拿到 Engine 的狀態碼與 body，無需輪詢 Kafka。
     *
     * @param exchange 目前 WebFlux 請求上下文
     * @return Engine 回應的 Mono
     */
    @GetMapping("/api/v1/**")
    public Mono<ResponseEntity<byte[]>> proxyGet(ServerWebExchange exchange) {
        return forward(exchange);
    }

    /**
     * 【職責】代理 {@code POST /api/v1/orders/{id}/fill}（成交指令）。
     * 【技巧】明確 mapping，避免與非同步 {@code POST /api/v1/orders} 衝突。
     * 【概念】成交仍需 Engine 同步處理並回結果，故不走 Kafka 下單 topic。
     *
     * @param exchange 目前請求
     * @return Engine 回應的 Mono
     */
    @PostMapping("/api/v1/orders/{id}/fill")
    public Mono<ResponseEntity<byte[]>> proxyPostFill(ServerWebExchange exchange) {
        return forward(exchange);
    }

    /**
     * 【職責】代理 {@code PATCH /api/v1/orders/{id}/cancel}（取消訂單）。
     * 【技巧】{@code @PatchMapping} 對應部分更新語意的取消操作。
     * 【概念】取消與查詢同屬「要 Engine 當下結果」的路徑，統一由本 Controller 轉發。
     *
     * @param exchange 目前請求
     * @return Engine 回應的 Mono
     */
    @PatchMapping("/api/v1/orders/{id}/cancel")
    public Mono<ResponseEntity<byte[]>> proxyPatch(ServerWebExchange exchange) {
        return forward(exchange);
    }

    /**
     * 從 exchange 抽出方法、路徑、查詢、標頭與 body，委派 {@link EngineProxyService#forward}。
     */
    private Mono<ResponseEntity<byte[]>> forward(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();

        return DataBufferUtils.join(request.getBody())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(body -> proxyService.forward(
                        request.getMethod(),
                        path,
                        request.getURI().getRawQuery(),
                        request.getHeaders(),
                        body));
    }
}
