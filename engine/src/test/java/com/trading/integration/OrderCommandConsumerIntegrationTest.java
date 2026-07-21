package com.trading.integration;

import com.trading.common.OrderCommandMessage;
import com.trading.common.Topics;
import com.trading.domain.OrderStatus;
import com.trading.infrastructure.repository.OrderEventRepository;
import com.trading.infrastructure.repository.OrderRepository;
import com.trading.infrastructure.repository.PositionRepository;
import com.trading.infrastructure.repository.TradeRepository;
import com.trading.support.GatewayMqTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("integration")
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = Topics.ORDER_COMMANDS)
@ActiveProfiles("test")
class OrderCommandConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @BeforeEach
    void clean() {
        orderEventRepository.deleteAll();
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
    }

    @Test
    void ENGINE_MQ_001_kafkaCommand_createsFilledOrder() throws Exception {
        OrderCommandMessage command = GatewayMqTestFixtures.loadCommand("GW-MQ-001-COMMAND");

        kafkaTemplate.send(Topics.ORDER_COMMANDS, command.getSymbol(), command).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(orderRepository.findByClientOrderId(command.getClientOrderId())).isPresent();
        });

        var order = orderRepository.findByClientOrderId(command.getClientOrderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(positionRepository.findBySymbol("BTCUSDT")).isPresent();
        assertThat(orderEventRepository.count()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void ENGINE_MQ_002_duplicateClientOrderId_onlyOneOrder() throws Exception {
        OrderCommandMessage command = GatewayMqTestFixtures.loadCommand("GW-MQ-001-COMMAND");

        kafkaTemplate.send(Topics.ORDER_COMMANDS, command.getSymbol(), command).get();
        kafkaTemplate.send(Topics.ORDER_COMMANDS, command.getSymbol(), command).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orderRepository.findByClientOrderId(command.getClientOrderId())).isPresent());

        assertThat(orderRepository.count()).isEqualTo(1);
    }
}
