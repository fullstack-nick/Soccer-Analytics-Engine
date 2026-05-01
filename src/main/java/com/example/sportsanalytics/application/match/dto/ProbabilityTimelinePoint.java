package com.example.sportsanalytics.application.match.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProbabilityTimelinePoint(
        UUID id,
        UUID matchId,
        UUID eventId,
        Long eventSequence,
        int minute,
        int homeScore,
        int awayScore,
        double homeWin,
        double draw,
        double awayWin,
        String modelVersion,
        Double modelConfidence,
        String coverageQuality,
        List<String> explanations,
        Map<String, Double> featureContributions,
        Instant createdAt
) {
    public ProbabilityTimelinePoint {
        explanations = List.copyOf(explanations == null ? List.of() : explanations);
        featureContributions = Map.copyOf(featureContributions == null ? Map.of() : featureContributions);
    }
}
