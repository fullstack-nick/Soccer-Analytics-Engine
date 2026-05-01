package com.example.sportsanalytics.analytics.backtest;

public record EvaluationMetricSummary(
        int matchCount,
        int sampleCount,
        double brierScore,
        double logLoss,
        double topPickAccuracy
) {
}
