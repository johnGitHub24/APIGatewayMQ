package com.trading.api;

import com.trading.application.ResourceNotFoundException;
import com.trading.application.TradeService;
import com.trading.config.GlobalExceptionHandler;
import com.trading.domain.ErrorCodes;
import com.trading.dto.TradeDetailResponse;
import com.trading.infrastructure.entity.TradeEntity;
import com.trading.infrastructure.mapper.OrderMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 【職責】保護成交查詢 API Happy 與 404。
 * 【技巧】{@code @WebMvcTest} + {@link GlobalExceptionHandler}。
 * 【概念】成交列表走分頁；單筆找不到必須是穩定 404 契約。
 */
@Tag("unit")
@WebMvcTest(controllers = TradeController.class)
@Import(GlobalExceptionHandler.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeService tradeService;

    @MockBean
    private OrderMapper orderMapper;

    @Test
    void listTrades_returnsPagedData() throws Exception {
        TradeEntity trade = new TradeEntity();
        TradeDetailResponse detail = new TradeDetailResponse();
        detail.setTradeId(1L);
        detail.setOrderId(9L);
        detail.setExecutedQty(new BigDecimal("0.5"));
        when(tradeService.list(isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(trade)));
        when(orderMapper.toTradeDetail(trade)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tradeId").value(1))
                .andExpect(jsonPath("$.data[0].orderId").value(9));
    }

    @Test
    void getTrade_missing_returns404() throws Exception {
        when(tradeService.getById(99L))
                .thenThrow(new ResourceNotFoundException(ErrorCodes.TRADE_NOT_FOUND, "missing"));

        mockMvc.perform(get("/api/v1/trades/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRADE_NOT_FOUND"));
    }
}
