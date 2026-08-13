package com.trading.api;

import com.trading.config.GlobalExceptionHandler;
import com.trading.dto.OrderResponse;
import com.trading.engine.web.OrderLookupController;
import com.trading.infrastructure.entity.OrderEntity;
import com.trading.infrastructure.mapper.OrderMapper;
import com.trading.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 【職責】保護依 clientOrderId 查單 Happy 與 404。
 * 【技巧】{@code @WebMvcTest(OrderLookupController)} 掛在 {@code com.trading.api} 以使用測試 Application。
 * 【概念】冪等追蹤靠外部鍵，找不到必須明確 404。
 */
@Tag("unit")
@WebMvcTest(controllers = OrderLookupController.class)
@Import(GlobalExceptionHandler.class)
class OrderLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OrderMapper orderMapper;

    @Test
    void findByClientOrderId_found_returnsOrder() throws Exception {
        OrderEntity entity = new OrderEntity();
        OrderResponse response = new OrderResponse();
        response.setOrderId(1L);
        response.setClientOrderId("demo-001");
        when(orderRepository.findByClientOrderId("demo-001")).thenReturn(Optional.of(entity));
        when(orderMapper.toResponse(entity)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders").param("clientOrderId", "demo-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.clientOrderId").value("demo-001"));
    }

    @Test
    void findByClientOrderId_missing_returns404() throws Exception {
        when(orderRepository.findByClientOrderId("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/orders").param("clientOrderId", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }
}
