package com.example.sportsanalytics.analytics.backtest;

import java.util.UUID;

public record BacktestMatchMetrics(
        UUID matchId,
        String providerMatchId,
        Outcome actualOutcome,
        int probabilitySnapshots,
        int inPlaySnapshots,
        int fixedMinuteSamples,
        double headlineBrierScore,
        double headlineLogLoss,
        boolean finalSnapshotTopPickCorrect
) {
}
