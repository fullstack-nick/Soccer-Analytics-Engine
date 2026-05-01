package com.example.sportsanalytics.analytics.backtest;

public record FixedMinuteMetric(
        String label,
        int targetMinute,
        EvaluationMetricSummary metrics
) {
}
