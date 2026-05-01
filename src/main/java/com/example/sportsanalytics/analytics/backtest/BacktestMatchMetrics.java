package com.example.sportsanalytics.analytics.backtest;

import java.util.UUID;

public record BacktestMatchMetrics(
        UUID matchId,
        String providerMatchId,
        Outcome actualOutcome,
        int probabilitySnapshots,
        double brierScore,
        double logLoss,
        boolean finalSnapshotTopPickCorrect
) {
}
