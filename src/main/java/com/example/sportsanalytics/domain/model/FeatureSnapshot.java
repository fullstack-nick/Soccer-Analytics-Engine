package com.example.sportsanalytics.domain.model;

import java.time.Instant;
import java.util.Objects;

public record FeatureSnapshot(
        String matchId,
        int minute,
        int scoreDifference,
        double timeRemaining,
        Double teamStrengthDelta,
        Double lineupAdjustment,
        Double redCardAdjustment,
        Double xgDelta,
        Double shotPressureDelta,
        Double fieldTilt,
        Double momentumDelta,
        Probability providerProbability,
        CoverageMode coverageMode,
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
        if (timeRemaining < 0) {
            throw new IllegalArgumentException("timeRemaining must be greater than or equal to 0");
        }
    }
}
