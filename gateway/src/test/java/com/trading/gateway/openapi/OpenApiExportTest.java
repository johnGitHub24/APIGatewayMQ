package com.trading.gateway.openapi;

import com.trading.common.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("openapi")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = Topics.ORDER_COMMANDS)
@ActiveProfiles("test")
class OpenApiExportTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveStringRedisTemplate redisTemplate;

    @MockBean
    private ReactiveValueOperations<String, String> valueOperations;

    @BeforeEach
    void mockRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
    }

    @Test
    void exportGatewayOpenApiYaml() {
        byte[] yaml = webTestClient.get()
                .uri("/v3/api-docs.yaml")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        try {
            Path out = Path.of("docs", "openapi-gateway-live.yaml").toAbsolutePath().normalize();
            Files.createDirectories(out.getParent());
            Files.write(out, yaml);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
