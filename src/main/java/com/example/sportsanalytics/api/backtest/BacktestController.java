package com.example.sportsanalytics.api.backtest;

import com.example.sportsanalytics.application.backtest.BacktestService;
import com.example.sportsanalytics.application.backtest.dto.BacktestRunView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backtests")
@RestController
@RequestMapping("/api/backtests")
public class BacktestController {
    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @Operation(summary = "Return a persisted backtest run")
    @GetMapping("/{runId}")
    public BacktestRunView backtest(@PathVariable UUID runId) {
        return backtestService.get(runId);
    }
}
