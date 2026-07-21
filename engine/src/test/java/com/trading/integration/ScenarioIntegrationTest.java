package com.trading.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import com.trading.dto.CreateOrderRequest;
import com.trading.infrastructure.repository.OrderEventRepository;
import com.trading.infrastructure.repository.OrderRepository;
import com.trading.infrastructure.repository.PositionRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScenarioIntegrationTest {

    private static final int HIGH_FREQ_COUNT = 50;
    private static final int RANDOM_FLOW_COUNT = 20;
    private static final long RANDOM_SEED = 42L;

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

    @BeforeEach
    @Transactional
    void clean() {
        orderEventRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
    }

    @Test
    @Transactional
    void SCN_001_RANDOM_FLOW_buySellMix_positionAndPnlConsistent() throws Exception {
        Random random = new Random(RANDOM_SEED);
        BigDecimal netQty = BigDecimal.ZERO;
        String symbol = "ETHUSDT";
        BigDecimal price = new BigDecimal("3500");

        for (int i = 0; i < RANDOM_FLOW_COUNT; i++) {
            OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
            BigDecimal qty = new BigDecimal("0.1");

            if (side == OrderSide.SELL && netQty.compareTo(qty) < 0) {
                side = OrderSide.BUY;
            }

            CreateOrderRequest request = new CreateOrderRequest();
            request.setSymbol(symbol);
            request.setSide(side);
            request.setQuantity(qty);
            request.setPrice(price);

            mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", "rnd-" + i + "-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            netQty = side == OrderSide.BUY ? netQty.add(qty) : netQty.subtract(qty);
        }

        var position = positionRepository.findBySymbol(symbol);
        assertThat(position).isPresent();
        assertThat(position.get().getQuantity()).isEqualByComparingTo(netQty);

        mockMvc.perform(get("/api/v1/pnl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnrealizedPnl").exists());
    }

    @Test
    @Transactional
    void SCN_002_HIGH_FREQ_manyValidOrders_allSucceedAndDbConsistent() throws Exception {
        CreateOrderRequest template = OrderTestFixtures.load("ORDER-HF-SMALL");
        int successCount = 0;

        for (int i = 0; i < HIGH_FREQ_COUNT; i++) {
            mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", "hf-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(template)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("FILLED"));
            successCount++;
        }

        assertThat(successCount).isEqualTo(HIGH_FREQ_COUNT);
        assertThat(orderRepository.count()).isEqualTo(HIGH_FREQ_COUNT);

        var position = positionRepository.findBySymbol("ETHUSDT");
        assertThat(position).isPresent();
        BigDecimal expectedQty = template.getQuantity().multiply(BigDecimal.valueOf(HIGH_FREQ_COUNT));
        assertThat(position.get().getQuantity()).isEqualByComparingTo(expectedQty);
    }

    @Test
    @Transactional
    void SCN_003_INVALID_BURST_mixedRequests_validSucceedInvalidRejected() throws Exception {
        CreateOrderRequest valid = OrderTestFixtures.load("ORDER-001-SUCCESS");
        CreateOrderRequest missingSymbol = OrderTestFixtures.load("ORDER-003-MISSING_REQUIRED");
        CreateOrderRequest positionLimit = OrderTestFixtures.load("ORDER-005-RISK_POSITION_LIMIT");

        List<Integer> statusCodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            statusCodes.add(mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", "burst-ok-" + i + "-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(valid)))
                    .andReturn().getResponse().getStatus());
        }

        for (int i = 0; i < 10; i++) {
            statusCodes.add(mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", "burst-bad-val-" + i + "-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(missingSymbol)))
                    .andReturn().getResponse().getStatus());
        }

        for (int i = 0; i < 10; i++) {
            statusCodes.add(mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", "burst-risk-" + i + "-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(positionLimit)))
                    .andReturn().getResponse().getStatus());
        }

        assertThat(statusCodes.stream().filter(s -> s == 201).count()).isEqualTo(10);
        assertThat(statusCodes.stream().filter(s -> s == 400).count()).isEqualTo(10);
        assertThat(statusCodes.stream().filter(s -> s == 422).count()).isEqualTo(10);
        assertThat(orderRepository.count()).isEqualTo(20);
    }

    @Test
    @Transactional
    void SCN_004_EXTREME_VOL_highVolatilitySymbol_rejected() throws Exception {
        CreateOrderRequest request = OrderTestFixtures.load("ORDER-005-RISK_VOLATILITY");

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RISK_VOLATILITY_LIMIT"))
                .andExpect(jsonPath("$.ruleCode").value("R005"));
    }

    @Test
    void SCN_005_CONCURRENT_parallelOrders_allPersisted() throws Exception {
        orderEventRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();

        CreateOrderRequest template = OrderTestFixtures.load("ORDER-HF-SMALL");
        int threadCount = 10;
        int ordersPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < ordersPerThread; i++) {
                        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                                        .header("Idempotency-Key", "conc-" + threadId + "-" + i + "-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(template)))
                                .andReturn();
                        if (result.getResponse().getStatus() == 201) {
                            success.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(success.get()).isGreaterThanOrEqualTo(40);
        assertThat(orderRepository.count()).isEqualTo(success.get());
        assertThat(orderRepository.findAll().stream().allMatch(o -> o.getStatus() == OrderStatus.FILLED)).isTrue();
    }
}
