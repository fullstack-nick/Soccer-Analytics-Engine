package com.example.sportsanalytics.analytics.backtest;

import java.util.List;
import java.util.UUID;

public record BacktestMatchSample(
        UUID matchId,
        String providerMatchId,
        Outcome actualOutcome,
        List<BacktestProbabilitySample> probabilities
) {
    public BacktestMatchSample {
        probabilities = List.copyOf(probabilities == null ? List.of() : probabilities);
    }
}
