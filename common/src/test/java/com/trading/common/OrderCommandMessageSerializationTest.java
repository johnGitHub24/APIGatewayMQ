package com.trading.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class OrderCommandMessageSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void COMMON_001_roundTripSerialization() throws Exception {
        Instant submittedAt = Instant.parse("2026-07-06T05:00:00Z");
        OrderCommandMessage original = OrderCommandMessage.builder()
                .commandId("cmd-001")
                .clientOrderId("client-001")
                .symbol("BTCUSDT")
                .side("BUY")
                .quantity(new BigDecimal("0.5"))
                .price(new BigDecimal("65000"))
                .submittedAt(submittedAt)
                .sourceGateway("gateway-1")
                .build();

        String json = objectMapper.writeValueAsString(original);
        OrderCommandMessage restored = objectMapper.readValue(json, OrderCommandMessage.class);

        assertThat(restored.getCommandId()).isEqualTo("cmd-001");
        assertThat(restored.getClientOrderId()).isEqualTo("client-001");
        assertThat(restored.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(restored.getSide()).isEqualTo("BUY");
        assertThat(restored.getQuantity()).isEqualByComparingTo("0.5");
        assertThat(restored.getPrice()).isEqualByComparingTo("65000");
        assertThat(restored.getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(restored.getSourceGateway()).isEqualTo("gateway-1");
    }

    @Test
    void COMMON_002_topicsConstant() {
        assertThat(Topics.ORDER_COMMANDS).isEqualTo("order.commands");
    }
}
