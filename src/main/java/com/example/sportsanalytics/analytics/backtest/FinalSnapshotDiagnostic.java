package com.example.sportsanalytics.analytics.backtest;

public record FinalSnapshotDiagnostic(
        int matchCount,
        double brierScore,
        double logLoss,
        double topPickAccuracy
) {
}
