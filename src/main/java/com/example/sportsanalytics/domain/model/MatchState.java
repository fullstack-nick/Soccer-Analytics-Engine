package com.example.sportsanalytics.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MatchState(
        String matchId,
        String homeTeamId,
        String awayTeamId,
        CoverageMode coverageMode,
        int minute,
        int homeScore,
        int awayScore,
        int homeRedCards,
        int awayRedCards,
        Map<String, Object> lineups,
        Integer latestMomentum,
        Map<String, Object> accumulatedStats,
        Instant updatedAt
) {
    public MatchState {
        matchId = requireText(matchId, "matchId");
        homeTeamId = requireText(homeTeamId, "homeTeamId");
        awayTeamId = requireText(awayTeamId, "awayTeamId");
        coverageMode = Objects.requireNonNull(coverageMode, "coverageMode is required");
        lineups = Map.copyOf(Objects.requireNonNullElse(lineups, Map.of()));
        accumulatedStats = Map.copyOf(Objects.requireNonNullElse(accumulatedStats, Map.of()));
        if (minute < 0 || homeScore < 0 || awayScore < 0 || homeRedCards < 0 || awayRedCards < 0) {
            throw new IllegalArgumentException("match state counters must be non-negative");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
