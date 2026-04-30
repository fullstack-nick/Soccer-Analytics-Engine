package com.example.sportsanalytics.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record FeatureSnapshot(
        String matchId,
        int minute,
        int scoreDifference,
        int timeRemainingMinutes,
        double timeRemainingRatio,
        double homeAdvantage,
        Double teamStrengthDelta,
        Double lineupAdjustment,
        Double redCardAdjustment,
        Double xgDelta,
        Double shotPressureDelta,
        Double shotLocationQualityDelta,
        Double fieldTilt,
        Double possessionPressureDelta,
        Double momentumTrend,
        Probability providerProbability,
        CoverageMode coverageMode,
        List<String> availableFeatures,
        List<String> missingFeatures,
        Instant createdAt
) {
    public FeatureSnapshot {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId is required");
        }
        coverageMode = Objects.requireNonNull(coverageMode, "coverageMode is required");
        if (minute < 0) {
            throw new IllegalArgumentException("minute must be greater than or equal to 0");
        }
        if (timeRemainingMinutes < 0) {
            throw new IllegalArgumentException("timeRemainingMinutes must be greater than or equal to 0");
        }
        if (!Double.isFinite(timeRemainingRatio) || timeRemainingRatio < 0.0) {
            throw new IllegalArgumentException("timeRemainingRatio must be finite and non-negative");
        }
        availableFeatures = List.copyOf(availableFeatures == null ? List.of() : availableFeatures);
        missingFeatures = List.copyOf(missingFeatures == null ? List.of() : missingFeatures);
    }
}
