package com.example.sportsanalytics.application.backtest.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BacktestRunView(
        UUID runId,
        String seasonId,
        String modelVersion,
        String status,
        int requestedMatchCount,
        int processedMatchCount,
        int failedMatchCount,
        Instant startedAt,
        Instant finishedAt,
        Map<String, Object> metrics,
        List<BacktestFailureView> failures
) {
    public BacktestRunView {
        metrics = Map.copyOf(metrics == null ? Map.of() : metrics);
        failures = List.copyOf(failures == null ? List.of() : failures);
    }
}
