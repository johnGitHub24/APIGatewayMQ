package com.trading.gateway.service;

import com.trading.common.OrderCommandMessage;
import com.trading.common.Topics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OrderCommandProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderCommandProducer producer;

    @Test
    void GW_001_publish_sendsToOrderCommandsTopicWithSymbolKey() {
        OrderCommandMessage command = OrderCommandMessage.builder()
                .commandId("cmd-1")
                .clientOrderId("client-1")
                .symbol("BTCUSDT")
                .side("BUY")
                .quantity(new BigDecimal("0.5"))
                .price(new BigDecimal("65000"))
                .submittedAt(Instant.now())
                .build();

        when(kafkaTemplate.send(eq(Topics.ORDER_COMMANDS), eq("BTCUSDT"), eq(command)))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publish(command);

        ArgumentCaptor<OrderCommandMessage> captor = ArgumentCaptor.forClass(OrderCommandMessage.class);
        verify(kafkaTemplate).send(eq(Topics.ORDER_COMMANDS), eq("BTCUSDT"), captor.capture());
        assertThat(captor.getValue().getClientOrderId()).isEqualTo("client-1");
    }
}
