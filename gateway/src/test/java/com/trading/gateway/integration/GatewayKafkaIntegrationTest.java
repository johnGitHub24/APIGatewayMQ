package com.trading.gateway.integration;

import com.trading.common.OrderCommandMessage;
import com.trading.common.Topics;
import com.trading.support.GatewayTestFixtures;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = Topics.ORDER_COMMANDS)
@ActiveProfiles("test")
class GatewayKafkaIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

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
    @DisplayName("GW-001 GW-003 post order appears on Kafka topic")
    void GW_003_postOrder_messageAppearsOnKafkaTopic() throws Exception {
        String body = GatewayTestFixtures.loadGatewayOrderJson("GW-ORDER-001-SUCCESS");

        webTestClient.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", "gw-kafka-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("gw-test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonDeserializer.class);
        consumerProps.put(org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES, "com.trading.common");
        consumerProps.put(org.springframework.kafka.support.serializer.JsonDeserializer.VALUE_DEFAULT_TYPE,
                OrderCommandMessage.class.getName());

        try (var consumer = new DefaultKafkaConsumerFactory<String, OrderCommandMessage>(consumerProps)
                .createConsumer()) {
            consumer.subscribe(Collections.singletonList(Topics.ORDER_COMMANDS));
            ConsumerRecord<String, OrderCommandMessage> record =
                    KafkaTestUtils.getSingleRecord(consumer, Topics.ORDER_COMMANDS, Duration.ofSeconds(10));

            assertThat(record.key()).isEqualTo("BTCUSDT");
            assertThat(record.value().getClientOrderId()).isEqualTo("gw-kafka-001");
            assertThat(record.value().getSymbol()).isEqualTo("BTCUSDT");
        }
    }

    @Test
    void GW_006_postOrder_missingSymbol_returns400() {
        webTestClient.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", "gw-kafka-bad")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"side\":\"BUY\",\"quantity\":0.5,\"price\":65000}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
