package com.example.sportsanalytics.analytics.backtest;

import java.util.List;
import java.util.Map;

public record BacktestMetrics(
        String evaluationVersion,
        EvaluationMetricSummary headline,
        List<FixedMinuteMetric> fixedMinuteMetrics,
        EvaluationMetricSummary allInPlay,
        FinalSnapshotDiagnostic finalSnapshotDiagnostic,
        BaselineMetrics baselines,
        List<MinuteBucketCalibration> minuteBucketCalibration,
        Map<String, Double> eventMovement,
        List<BacktestMatchMetrics> matchMetrics
) {
    public BacktestMetrics {
        fixedMinuteMetrics = List.copyOf(fixedMinuteMetrics == null ? List.of() : fixedMinuteMetrics);
        minuteBucketCalibration = List.copyOf(minuteBucketCalibration == null ? List.of() : minuteBucketCalibration);
        eventMovement = Map.copyOf(eventMovement == null ? Map.of() : eventMovement);
        matchMetrics = List.copyOf(matchMetrics == null ? List.of() : matchMetrics);
    }
}
