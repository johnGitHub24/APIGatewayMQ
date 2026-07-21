package com.trading.gateway.filter;

import com.trading.gateway.config.GatewayProperties;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 【職責】Gateway 入口全域限流：依客戶端鍵在 Redis 做每秒固定視窗計數，超限回 429。
 * 【技巧】WebFlux {@link WebFilter}；{@link ReactiveStringRedisTemplate} 的 INCR + 首次設 TTL 1 秒；
 *         {@code @ConditionalOnProperty(gateway.rate-limit.enabled)} 可關閉（預設啟用）。
 *         本實作是固定視窗計數，非 Bucket4j 令牌桶；多實例共享同一 Redis 鍵即可跨節點限流。
 * 【概念】限流放在 Filter 而非 Controller：所有 {@code /api/**} 在進業務前就被保護，避免打爆下游 Kafka／Engine。
 *         Redis 故障時 {@code onErrorResume} 放行，優先可用性（可依需求改為 fail-closed）。
 * 【邊界】不解析業務 body、不做認證；只決定「放行或 429」。閾值來自 {@link GatewayProperties#getRateLimitPerSecond()}。
 */
@Component
@ConditionalOnProperty(name = "gateway.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitWebFilter implements WebFilter {

    /** Redis 鍵前綴，完整鍵為 {@code gateway:rate:<clientKey>}。 */
    private static final String KEY_PREFIX = "gateway:rate:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayProperties properties;

    /**
     * 【職責】注入反應式 Redis 與限流設定。
     * 【技巧】建構子注入，便於測試替換 Mock。
     * 【概念】Filter 本身無狀態；計數狀態存在 Redis，多 Gateway 副本可共用配額。
     *
     * @param redisTemplate 反應式 Redis，用於 INCR 與 TTL
     * @param properties    提供每秒上限
     */
    public RateLimitWebFilter(ReactiveStringRedisTemplate redisTemplate, GatewayProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 【職責】對每個請求做 1 秒視窗計數；超過上限則寫 429 JSON，否則交給後續鏈。
     * 【技巧】{@code flatMap} 串起 INCR →（count==1 時 EXPIRE）→ 判斷；超限用 {@code writeWith} 寫 body，
     *         未超限 {@code chain.filter(exchange)}；{@code onErrorResume} 在 Redis 異常時放行。
     * 【概念】固定視窗：同一秒內計數歸同一 key；下一秒 key 過期後重新從 1 起算。
     *         與令牌桶相比實作簡單，但視窗邊界可能出現短暫突衝。
     *
     * @param exchange 目前請求／回應
     * @param chain    通過限流後的濾器與控制器鏈
     * @return 完成回應或繼續鏈的 {@link Mono}
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientKey = resolveClientKey(exchange);
        String redisKey = KEY_PREFIX + clientKey;

        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    Mono<Boolean> expire = count == 1
                            ? redisTemplate.expire(redisKey, Duration.ofSeconds(1))
                            : Mono.just(true);
                    return expire.thenReturn(count);
                })
                .flatMap(count -> {
                    if (count > properties.getRateLimitPerSecond()) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().add("Retry-After", "1");
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        String body = """
                                {"errorCode":"RATE_LIMIT_EXCEEDED","message":"Too many requests, retry later"}
                                """;
                        DataBuffer buffer = exchange.getResponse().bufferFactory()
                                .wrap(body.getBytes(StandardCharsets.UTF_8));
                        return exchange.getResponse().writeWith(Mono.just(buffer));
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(ex -> chain.filter(exchange));
    }

    /**
     * 解析限流客戶端鍵：優先 {@code X-Forwarded-For} 第一個 IP，否則 remoteAddress。
     */
    private String resolveClientKey(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null
                && exchange.getRequest().getRemoteAddress().getAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
