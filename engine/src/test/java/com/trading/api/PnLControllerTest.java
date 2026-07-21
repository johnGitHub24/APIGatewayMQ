package com.trading.api;

import com.trading.application.PnLService;
import com.trading.dto.PnLResponse;
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
@WebMvcTest(PnLController.class)
class PnLControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PnLService pnlService;

    @Test
    void getPnl_returnsSummary() throws Exception {
        PnLResponse response = new PnLResponse();
        response.setTotalUnrealizedPnl(new BigDecimal("100"));
        response.setAsOf(OffsetDateTime.now());
        response.setPositions(List.of());

        when(pnlService.getSummary()).thenReturn(response);

        mockMvc.perform(get("/api/v1/pnl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnrealizedPnl").value(100));
    }
}
