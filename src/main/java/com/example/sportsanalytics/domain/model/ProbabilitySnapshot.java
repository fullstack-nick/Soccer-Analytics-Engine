package com.example.sportsanalytics.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ProbabilitySnapshot(
        String matchId,
        UUID eventId,
        int minute,
        Probability probability,
        CoverageMode coverageMode,
        Double modelConfidence,
        List<String> explanations,
        Map<String, Double> featureContributions,
        Instant createdAt
) {
    public ProbabilitySnapshot {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId is required");
        }
        probability = Objects.requireNonNull(probability, "probability is required");
        coverageMode = Objects.requireNonNull(coverageMode, "coverageMode is required");
        explanations = List.copyOf(Objects.requireNonNullElse(explanations, List.of()));
        featureContributions = Map.copyOf(Objects.requireNonNullElse(featureContributions, Map.of()));
        if (minute < 0) {
            throw new IllegalArgumentException("minute must be greater than or equal to 0");
        }
    }
}
