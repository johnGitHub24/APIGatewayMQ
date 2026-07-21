package com.trading.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.application.TradingService;
import com.trading.config.GlobalExceptionHandler;
import com.trading.config.GlobalExceptionHandler;
import org.springframework.context.annotation.Import;
import com.trading.domain.OrderSide;
import com.trading.domain.OrderStatus;
import com.trading.dto.CreateOrderRequest;
import com.trading.dto.OrderResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("unit")
@WebMvcTest(controllers = OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TradingService tradingService;

    @MockBean
    private com.trading.application.OrderService orderService;

    @MockBean
    private com.trading.infrastructure.mapper.OrderMapper orderMapper;

    @MockBean
    private com.trading.application.TradeService tradeService;

    @MockBean
    private com.trading.infrastructure.repository.OrderEventRepository orderEventRepository;

    @Test
    void ORDER_001_success_returns201() throws Exception {
        OrderResponse response = new OrderResponse();
        response.setOrderId(1L);
        response.setSymbol("BTCUSDT");
        response.setStatus(OrderStatus.FILLED);
        when(tradingService.placeOrder(any(), isNull())).thenReturn(response);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setSymbol("BTCUSDT");
        request.setSide(OrderSide.BUY);
        request.setQuantity(new BigDecimal("0.5"));
        request.setPrice(new BigDecimal("65000"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("FILLED"));
    }

    @Test
    void ORDER_003_missingSymbol_returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setSide(OrderSide.BUY);
        request.setQuantity(new BigDecimal("0.5"));
        request.setPrice(new BigDecimal("65000"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
