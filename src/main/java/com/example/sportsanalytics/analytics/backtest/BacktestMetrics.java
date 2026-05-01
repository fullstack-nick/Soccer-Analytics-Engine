package com.example.sportsanalytics.analytics.backtest;

import java.util.List;
import java.util.Map;

public record BacktestMetrics(
        int matchCount,
        int probabilitySnapshotCount,
        double brierScore,
        double logLoss,
        double finalSnapshotTopPickAccuracy,
        List<CalibrationBucket> calibrationBuckets,
        Map<String, Double> averageProbabilityMovementByEventType,
        List<BacktestMatchMetrics> matchMetrics
) {
    public BacktestMetrics {
        calibrationBuckets = List.copyOf(calibrationBuckets == null ? List.of() : calibrationBuckets);
        averageProbabilityMovementByEventType = Map.copyOf(
                averageProbabilityMovementByEventType == null ? Map.of() : averageProbabilityMovementByEventType
        );
        matchMetrics = List.copyOf(matchMetrics == null ? List.of() : matchMetrics);
    }
}
