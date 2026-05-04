package com.example.sportsanalytics.api.season;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sportsanalytics.api.backtest.BacktestController;
import com.example.sportsanalytics.api.error.ApiExceptionHandler;
import com.example.sportsanalytics.application.backtest.BacktestService;
import com.example.sportsanalytics.application.backtest.dto.BacktestRunView;
import com.example.sportsanalytics.application.backtest.dto.RunBacktestCommand;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({SeasonBacktestController.class, BacktestController.class})
@Import({ApiExceptionHandler.class, SeasonBacktestControllerTest.TestBacktestConfiguration.class})
class SeasonBacktestControllerTest {
    private static final UUID RUN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BacktestService backtestService;

    @Test
    void runsBacktest() throws Exception {
        when(backtestService.run(any(RunBacktestCommand.class))).thenReturn(runView());

        mockMvc.perform(post("/api/seasons/{seasonId}/backtests", "sr:season:1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportEventIds\":[\"sr:sport_event:1\"],\"forceRefresh\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.metrics.evaluationVersion").value("stage5.5-v1"));
    }

    @Test
    void rejectsBlankSelectedSportEventId() throws Exception {
        mockMvc.perform(post("/api/seasons/{seasonId}/backtests", "sr:season:1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sportEventIds\":[\"\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBacktestRun() throws Exception {
        when(backtestService.get(RUN_ID)).thenReturn(runView());

        mockMvc.perform(get("/api/backtests/{runId}", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID.toString()));
    }

    private BacktestRunView runView() {
        return new BacktestRunView(
                RUN_ID,
                "sr:season:1",
                "xg-poisson-v1.3",
                "COMPLETED",
                1,
                1,
                0,
                Instant.parse("2026-04-30T00:00:00Z"),
                Instant.parse("2026-04-30T00:00:01Z"),
                Map.of(
                        "evaluationVersion", "stage5.5-v1",
                        "headline", Map.of("sampleCount", 1)
                ),
                List.of()
        );
    }

    @TestConfiguration
    static class TestBacktestConfiguration {
        @Bean
        BacktestService backtestService() {
            return mock(BacktestService.class);
        }
    }
}

