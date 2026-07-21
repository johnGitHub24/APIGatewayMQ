package com.trading.gateway.web;

import com.trading.common.Topics;
import com.trading.gateway.service.OrderCommandProducer;
import com.trading.support.GatewayTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("unit")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = Topics.ORDER_COMMANDS)
@ActiveProfiles("test")
class OrderSubmitControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderCommandProducer producer;

    @MockBean
    private ReactiveStringRedisTemplate redisTemplate;

    @MockBean
    private ReactiveValueOperations<String, String> valueOperations;

    @BeforeEach
    void mockRedisRateLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
    }

    @Test
    void GW_002_postOrder_returns202Accepted() throws Exception {
        when(producer.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

        String body = GatewayTestFixtures.loadGatewayOrderJson("GW-ORDER-001-SUCCESS");

        webTestClient.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", "gw-test-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.clientOrderId").isEqualTo("gw-test-001")
                .jsonPath("$.pollUrl").value(org.hamcrest.Matchers.containsString("clientOrderId="));
    }
}
