package com.trading.gateway.web;

import com.trading.gateway.config.GatewayProperties;
import com.trading.gateway.service.OrderCommandProducer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@Tag("unit")
@WebFluxTest(controllers = OrderSubmitController.class)
@ActiveProfiles("test")
class OrderSubmitControllerValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderCommandProducer producer;

    @MockBean
    private GatewayProperties gatewayProperties;

    @Test
    void GW_006_missingSymbol_returns400() {
        webTestClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"side\":\"BUY\",\"quantity\":0.5,\"price\":65000}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
