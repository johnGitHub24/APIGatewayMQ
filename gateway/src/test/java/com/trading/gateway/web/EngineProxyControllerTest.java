package com.trading.gateway.web;

import com.trading.gateway.config.GatewayProperties;
import com.trading.gateway.service.EngineProxyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 【職責】保護 Engine 同步代理 Happy 與下游錯誤透傳。
 * 【技巧】{@code @WebFluxTest} Mock {@link EngineProxyService}。
 * 【概念】查詢走代理拿立刻結果；Gateway 不重寫 Engine 狀態碼。
 */
@Tag("unit")
@WebFluxTest(controllers = EngineProxyController.class)
@ActiveProfiles("test")
class EngineProxyControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private EngineProxyService proxyService;

    @MockBean
    private GatewayProperties gatewayProperties;

    @MockBean
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    void proxyGet_happy_returnsEngineBody() {
        when(proxyService.forward(any(), anyString(), any(), any(HttpHeaders.class), any()))
                .thenReturn(Mono.just(ResponseEntity.ok().body("{\"orderId\":1}".getBytes())));

        webTestClient.get()
                .uri("/api/v1/orders?clientOrderId=demo-001")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("{\"orderId\":1}");
    }

    @Test
    void proxyGet_engineNotFound_forwards404() {
        when(proxyService.forward(any(), anyString(), any(), any(HttpHeaders.class), any()))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"errorCode\":\"ORDER_NOT_FOUND\"}".getBytes())));

        webTestClient.get()
                .uri("/api/v1/orders?clientOrderId=missing")
                .exchange()
                .expectStatus().isNotFound();
    }
}
