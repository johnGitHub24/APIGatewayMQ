package com.trading.gateway.service;

import com.trading.gateway.config.GatewayProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【職責】將 Gateway 收到的 HTTP 請求同步轉發到後端 Engine（查詢／成交／取消等代理路徑）。
 * 【技巧】每個 Engine URI 一個 {@link WebClient}；{@link AtomicInteger} + {@code floorMod} 做無鎖 round-robin；
 *         {@code exchangeToMono} 保留下游狀態碼與 headers，body 以 {@code byte[]} 透傳。
 * 【概念】與 Kafka 非同步下單互補：需要「立刻拿到 Engine HTTP 回應」的操作走此服務；
 *         寫入削峰走 MQ，讀取／狀態變更可走代理，避免 Gateway 自己實作訂單狀態機。
 * 【邊界】不解析業務 JSON、不做風控；僅轉發。Engine 清單來自 {@link GatewayProperties#getEngineUris()}。
 */
@Service
public class EngineProxyService {

    /** 每個 Engine baseUrl 對應一個可重用的 WebClient。 */
    private final List<WebClient> clients;

    /** 無鎖輪詢計數器，在多個 Engine 間分散請求。 */
    private final AtomicInteger roundRobin = new AtomicInteger();

    /**
     * 【職責】依設定為每個 Engine URI 建立專用 {@link WebClient}。
     * 【技巧】{@code stream().map(baseUrl -> builder.baseUrl(baseUrl).build()).toList()}。
     * 【概念】啟動時建好 client，請求路徑只做選節點與送出，避免每次 new 連線設定。
     *
     * @param properties       內含 engineUris
     * @param webClientBuilder 由 {@link com.trading.gateway.config.WebClientConfig} 提供
     */
    public EngineProxyService(GatewayProperties properties, WebClient.Builder webClientBuilder) {
        this.clients = properties.getEngineUris().stream()
                .map(baseUrl -> webClientBuilder.baseUrl(baseUrl).build())
                .toList();
    }

    /**
     * 【職責】將請求原樣轉發到選中的 Engine，回傳狀態碼與位元組本文。
     * 【技巧】WebFlux {@link Mono}；有 body 且方法允許時用 {@link BodyInserters#fromValue}；
     *         {@code defaultIfEmpty(new byte[0])} 處理空回應。
     * 【概念】回傳 {@code byte[]} 而非強型別 DTO，讓 Gateway 當「透明代理」，不必跟 Engine API 欄位耦合。
     * 【邊界】{@code clients} 為空時回 {@link Mono#error}；不重試失敗節點（可日後加）。
     *
     * @param method          HTTP 方法
     * @param path            應用內路徑
     * @param query           原始查詢字串，可為 null
     * @param incomingHeaders 客戶端請求標頭
     * @param body            請求本文；無本文時可為空陣列
     * @return Engine 回應的 {@link Mono}
     */
    public Mono<ResponseEntity<byte[]>> forward(HttpMethod method, String path, String query,
                                                 HttpHeaders incomingHeaders, byte[] body) {
        if (clients.isEmpty()) {
            return Mono.error(new IllegalStateException("No engine URIs configured"));
        }
        WebClient client = clients.get(Math.floorMod(roundRobin.getAndIncrement(), clients.size()));
        WebClient.RequestBodySpec spec = client.method(method)
                .uri(uriBuilder -> uriBuilder.path(path).query(query).build())
                .headers(headers -> copyHeaders(incomingHeaders, headers));

        Mono<ResponseEntity<byte[]>> responseMono;
        if (body != null && body.length > 0 && allowsBody(method)) {
            responseMono = spec.contentType(resolveContentType(incomingHeaders))
                    .body(BodyInserters.fromValue(body))
                    .exchangeToMono(response -> response.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .map(bytes -> ResponseEntity.status(response.statusCode())
                                    .headers(response.headers().asHttpHeaders())
                                    .body(bytes)));
        } else {
            responseMono = spec.exchangeToMono(response -> response.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(bytes -> ResponseEntity.status(response.statusCode())
                            .headers(response.headers().asHttpHeaders())
                            .body(bytes)));
        }
        return responseMono;
    }

    /** 複製可轉發標頭，略過 Host／Content-Length 以免與下游衝突。 */
    private void copyHeaders(HttpHeaders source, HttpHeaders target) {
        source.forEach((name, values) -> {
            if (HttpHeaders.HOST.equalsIgnoreCase(name)
                    || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                return;
            }
            target.put(name, values);
        });
    }

    /** 決定轉發 Content-Type；未帶則預設 JSON。 */
    private MediaType resolveContentType(HttpHeaders headers) {
        MediaType type = headers.getContentType();
        return type != null ? type : MediaType.APPLICATION_JSON;
    }

    /** 判斷 HTTP 方法是否允許攜帶 request body。 */
    private boolean allowsBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }
}
