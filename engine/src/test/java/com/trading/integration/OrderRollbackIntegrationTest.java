package com.trading.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.application.PositionService;
import com.trading.domain.OrderSide;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderRollbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Autowired
    private PositionRepository positionRepository;

    @MockBean
    private PositionService positionService;

    @BeforeEach
    void clean() {
        orderEventRepository.deleteAll();
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
        when(positionService.getCurrentQuantity(any())).thenReturn(BigDecimal.ZERO);
        when(positionService.getTotalExposure()).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void ORDER_007_ROLLBACK_midFailure_noResidue() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-001-SUCCESS");

        when(positionService.updateAfterFill(any(), any(OrderSide.class), any(), any()))
                .thenThrow(new RuntimeException("simulated failure"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        assertThat(orderRepository.count()).isZero();
        assertThat(tradeRepository.count()).isZero();
        assertThat(orderEventRepository.count()).isZero();
    }
}
