package com.trading.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class AdvancedRiskIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void R007_CHOPPY_market_rejects() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-R007-CHOPPY");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RISK_MARKET_CHOPPY"))
                .andExpect(jsonPath("$.ruleCode").value("R007"));
    }

    @Test
    void R010_HIGHVOL_reducesQuantity() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-R010-HIGHVOL");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(5.0));

        assertThat(orderEventRepository.findAll().stream()
                .anyMatch(e -> e.getEvent().name().equals("OUTCOME_RECORDED"))).isTrue();
    }
}
