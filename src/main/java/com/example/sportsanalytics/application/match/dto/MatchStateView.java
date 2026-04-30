package com.example.sportsanalytics.application.match.dto;

import com.example.sportsanalytics.domain.model.CoverageMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MatchStateView(
        UUID matchId,
        UUID eventId,
        String providerMatchId,
        CoverageMode coverageMode,
        long stateVersion,
        TeamView homeTeam,
        TeamView awayTeam,
        int minute,
        int homeScore,
        int awayScore,
        int homeRedCards,
        int awayRedCards,
        Map<String, Object> state,
        Instant updatedAt
) {
}
