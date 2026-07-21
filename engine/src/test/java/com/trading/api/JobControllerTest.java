package com.trading.api;

import com.trading.application.DataCleanupService;
import com.trading.application.FailedCommandService;
import com.trading.application.PnlSnapshotService;
import com.trading.application.StaleOrderCancellationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StaleOrderCancellationService staleOrderCancellationService;

    @MockBean
    private PnlSnapshotService pnlSnapshotService;

    @MockBean
    private FailedCommandService failedCommandService;

    @MockBean
    private DataCleanupService dataCleanupService;

    @Test
    void JOB_API_001_runStaleOrderCancellation_returnsAffectedCount() throws Exception {
        when(staleOrderCancellationService.cancelStaleOrders()).thenReturn(3);

        mockMvc.perform(post("/api/v1/jobs/stale-order-cancellation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("JOB-A"))
                .andExpect(jsonPath("$.affected").value(3));
    }

    @Test
    void JOB_API_002_runPnlSnapshot_returnsAffectedCount() throws Exception {
        when(pnlSnapshotService.captureSnapshot()).thenReturn(2);

        mockMvc.perform(post("/api/v1/jobs/pnl-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("JOB-B"))
                .andExpect(jsonPath("$.affected").value(2));
    }

    @Test
    void JOB_API_003_runFailedCommandRetry_returnsAffectedCount() throws Exception {
        when(failedCommandService.retryFailedCommands()).thenReturn(1);

        mockMvc.perform(post("/api/v1/jobs/failed-command-retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("JOB-C"))
                .andExpect(jsonPath("$.affected").value(1));
    }

    @Test
    void JOB_API_004_runCleanup_returnsTotalAndDetail() throws Exception {
        when(dataCleanupService.cleanup())
                .thenReturn(new DataCleanupService.CleanupResult(5, 2));

        mockMvc.perform(post("/api/v1/jobs/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("JOB-D"))
                .andExpect(jsonPath("$.affected").value(7))
                .andExpect(jsonPath("$.detail").value("events=5, failedCommands=2"));
    }

    @Test
    void JOB_API_005_listPnlSnapshots_returnsEmptyList() throws Exception {
        when(pnlSnapshotService.findByDate(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/pnl-snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void JOB_API_006_listFailedCommands_returnsEmptyList() throws Exception {
        when(failedCommandService.findByStatus(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/failed-commands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
