package com.example.sportsanalytics.api.season;

import com.example.sportsanalytics.application.backtest.BacktestService;
import com.example.sportsanalytics.application.backtest.dto.BacktestRunView;
import com.example.sportsanalytics.application.backtest.dto.RunBacktestCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backtests")
@Validated
@RestController
@RequestMapping("/api/seasons")
public class SeasonBacktestController {
    private final BacktestService backtestService;

    public SeasonBacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @Operation(summary = "Run a synchronous backtest for a full season or selected sport events")
    @PostMapping("/{seasonId}/backtests")
    public BacktestRunView runBacktest(
            @PathVariable String seasonId,
            @Valid @RequestBody(required = false) BacktestRequest request
    ) {
        BacktestRequest value = request == null ? new BacktestRequest(List.of(), false, true) : request;
        return backtestService.run(new RunBacktestCommand(
                seasonId,
                value.sportEventIds(),
                value.forceRefresh(),
                value.continueOnMatchFailure() == null || value.continueOnMatchFailure()
        ));
    }
}
