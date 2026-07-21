package com.trading.api;

import com.trading.application.PositionService;
import com.trading.infrastructure.entity.PositionEntity;
import com.trading.infrastructure.mapper.OrderMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@WebMvcTest(PositionController.class)
class PositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PositionService positionService;

    @MockBean
    private OrderMapper orderMapper;

    @Test
    void getPositions_returnsList() throws Exception {
        PositionEntity entity = new PositionEntity();
        entity.setSymbol("BTCUSDT");
        entity.setQuantity(new BigDecimal("1"));
        entity.setAvgPrice(new BigDecimal("65000"));
        entity.setUnrealizedPnl(BigDecimal.ZERO);
        entity.setUpdatedAt(OffsetDateTime.now());

        when(positionService.findAll()).thenReturn(List.of(entity));
        when(orderMapper.toPositionResponse(entity)).thenAnswer(inv -> {
            var resp = new com.trading.dto.PositionResponse();
            resp.setSymbol("BTCUSDT");
            resp.setQuantity(new BigDecimal("1"));
            return resp;
        });

        mockMvc.perform(get("/api/v1/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].symbol").value("BTCUSDT"));
    }
}
