package com.example.sportsanalytics.analytics.backtest;

public record CalibrationBucket(
        String bucket,
        int count,
        double averageConfidence,
        double empiricalAccuracy
) {
}
