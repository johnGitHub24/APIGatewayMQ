package com.trading.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.domain.OrderEventType;
import com.trading.domain.OrderStatus;
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
class PlaceOrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private OrderEventRepository orderEventRepository;

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
    void ORDER_001_SUCCESS_placeOrder_fillsAndUpdatesPosition() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-001-SUCCESS");
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(positionRepository.findBySymbol("BTCUSDT")).isPresent();
        assertThat(orderEventRepository.count()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void ORDER_002_BOUNDARY_MAX_atLimit_succeeds() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-002-BOUNDARY_MAX");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIALLY_FILLED"));

        assertThat(positionRepository.findBySymbol("BTCUSDT")).isPresent();
        assertThat(positionRepository.findBySymbol("BTCUSDT").get().getQuantity())
                .isEqualByComparingTo("50");
    }

    @Test
    void ORDER_002_BOUNDARY_EXCEED_rejects() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-002-BOUNDARY_EXCEED");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RISK_POSITION_LIMIT"))
                .andExpect(jsonPath("$.ruleCode").value("R001"));
    }

    @Test
    void ORDER_003_MISSING_REQUIRED_returns400() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-003-MISSING_REQUIRED");

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void ORDER_004_INVALID_FORMAT_returns422() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-004-INVALID_FORMAT");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RISK_INVALID_ORDER"))
                .andExpect(jsonPath("$.ruleCode").value("R003"));
    }

    @Test
    void ORDER_005_RISK_POSITION_LIMIT_returns422() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-005-RISK_POSITION_LIMIT");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RISK_POSITION_LIMIT"))
                .andExpect(jsonPath("$.ruleCode").value("R001"));

        assertThat(orderRepository.findAll().stream()
                .allMatch(o -> o.getStatus() == OrderStatus.REJECTED)).isTrue();
    }

    @Test
    void ORDER_006_DUPLICATE_sameIdempotencyKey_returns409() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-001-SUCCESS");
        String key = "fixed-dup-key-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ORDER"));

        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void ORDER_405_postOnGetOnlyEndpoint_returns405() throws Exception {
        mockMvc.perform(post("/api/v1/pnl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void partialFill_thenCompleteFill_reachesFilled() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-PARTIAL-FILL");

        String body = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIALLY_FILLED"))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(body).get("orderId").asLong();

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/fill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FILLED"));

        assertThat(tradeRepository.findByOrderId(orderId)).hasSize(2);
    }

    @Test
    void cancelPartiallyFilledOrder_succeeds() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-PARTIAL-FILL");

        String body = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(body).get("orderId").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void getOrderEvents_returnsAuditTrail() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-001-SUCCESS");

        String body = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(body).get("orderId").asLong();

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].event").value("RECEIVED"))
                .andExpect(jsonPath("$.events[?(@.event=='FILLED')]").exists());
    }
}
