package com.example.sportsanalytics.analytics.backtest;

public record BaselineMetrics(
        EvaluationMetricSummary random,
        EvaluationMetricSummary scoreOnly,
        EvaluationMetricSummary providerPreMatch
) {
}
