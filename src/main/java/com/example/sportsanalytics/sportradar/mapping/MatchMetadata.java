package com.example.sportsanalytics.sportradar.mapping;

import java.time.Instant;

public record MatchMetadata(
        String providerMatchId,
        String seasonId,
        String competitionId,
        TeamMetadata homeTeam,
        TeamMetadata awayTeam,
        Instant startTime,
        int homeScore,
        int awayScore,
        String status,
        String matchStatus
) {
}
