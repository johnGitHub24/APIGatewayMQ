package com.trading.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.domain.OrderEventType;
import com.trading.dto.CreateOrderRequest;
import com.trading.infrastructure.repository.OrderEventRepository;
import com.trading.infrastructure.repository.OrderRepository;
import com.trading.infrastructure.repository.PositionRepository;
import com.trading.infrastructure.repository.TradeRepository;
import com.trading.support.OrderTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @BeforeEach
    void clean() {
        orderEventRepository.deleteAll();
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
    }

    @Test
    void LOG_001_REJECT_TRACE_hasFullRejectChain() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-005-RISK_POSITION_LIMIT");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());

        Long orderId = orderRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.event=='RECEIVED')]").exists())
                .andExpect(jsonPath("$.events[?(@.event=='RISK_CHECK')]").exists())
                .andExpect(jsonPath("$.events[?(@.event=='REJECTED')]").exists());

        assertThat(orderEventRepository.findByOrderIdOrderByCreatedAtAsc(orderId))
                .extracting(e -> e.getEvent())
                .contains(OrderEventType.RECEIVED, OrderEventType.RISK_CHECK, OrderEventType.REJECTED);
    }

    @Test
    void LOG_002_SUCCESS_TRACE_hasFullFillChain() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-001-SUCCESS");

        String body = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(body).get("orderId").asLong();

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/events"))
                .andExpect(jsonPath("$.events[?(@.event=='RECEIVED')]").exists())
                .andExpect(jsonPath("$.events[?(@.event=='APPROVED')]").exists())
                .andExpect(jsonPath("$.events[?(@.event=='FILLED')]").exists())
                .andExpect(jsonPath("$.events[?(@.event=='POSITION_UPDATED')]").exists());
    }

    @Test
    void LOG_003_DUPLICATE_noSecondRejectRecord() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-001-SUCCESS");
        String key = "log-dup-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderEventRepository.count()).isGreaterThanOrEqualTo(5);
    }
}
