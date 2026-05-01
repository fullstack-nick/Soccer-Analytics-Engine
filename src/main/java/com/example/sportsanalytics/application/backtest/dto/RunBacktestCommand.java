package com.example.sportsanalytics.application.backtest.dto;

import java.util.List;

public record RunBacktestCommand(
        String seasonId,
        List<String> sportEventIds,
        boolean forceRefresh,
        boolean continueOnMatchFailure
) {
    public RunBacktestCommand {
        sportEventIds = List.copyOf(sportEventIds == null ? List.of() : sportEventIds);
    }
}
