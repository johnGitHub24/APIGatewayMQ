package com.trading.gateway.service;

import com.trading.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

/**
 * 【職責】保護 {@link EngineProxyService#forward}：無 Engine 時失敗；有節點時透傳狀態與 body。
 * 【技巧】{@code WebClient.builder().exchangeFunction} 模擬下游，免真實 HTTP。
 * 【概念】Gateway 代理不解析業務 JSON，只轉發位元組與狀態碼。
 */
@Tag("unit")
class EngineProxyServiceTest {

    @Test
    void forward_noEngineUris_errors() {
        GatewayProperties properties = new GatewayProperties();
        properties.setEngineUris(List.of());
        EngineProxyService service = new EngineProxyService(properties, WebClient.builder());

        StepVerifier.create(service.forward(HttpMethod.GET, "/api/v1/orders", null,
                        new HttpHeaders(), new byte[0]))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void forward_engineOk_returnsStatusAndBody() {
        GatewayProperties properties = new GatewayProperties();
        properties.setEngineUris(List.of("http://engine-1"));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"orderId\":1}")
                        .build()));
        EngineProxyService service = new EngineProxyService(properties, builder);

        StepVerifier.create(service.forward(HttpMethod.GET, "/api/v1/orders", "clientOrderId=demo",
                        new HttpHeaders(), new byte[0]))
                .assertNext(response -> {
                    assertStatus(response, 200);
                    assertThatBody(response, "{\"orderId\":1}");
                })
                .verifyComplete();
    }

    private static void assertStatus(ResponseEntity<byte[]> response, int expected) {
        if (response.getStatusCode().value() != expected) {
            throw new AssertionError("expected status " + expected + " but was " + response.getStatusCode());
        }
    }

    private static void assertThatBody(ResponseEntity<byte[]> response, String expected) {
        String actual = response.getBody() == null ? "" : new String(response.getBody());
        if (!expected.equals(actual)) {
            throw new AssertionError("expected body " + expected + " but was " + actual);
        }
    }
}
