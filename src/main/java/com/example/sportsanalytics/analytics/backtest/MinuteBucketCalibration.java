package com.example.sportsanalytics.analytics.backtest;

public record MinuteBucketCalibration(
        String bucket,
        int sampleCount,
        double averageConfidence,
        double empiricalAccuracy
) {
}
