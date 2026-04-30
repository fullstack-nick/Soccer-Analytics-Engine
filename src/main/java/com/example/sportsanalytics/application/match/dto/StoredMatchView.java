package com.example.sportsanalytics.application.match.dto;

import com.example.sportsanalytics.domain.model.CoverageMode;
import java.time.Instant;
import java.util.UUID;

public record StoredMatchView(
        UUID matchId,
        String providerMatchId,
        String seasonId,
        String competitionId,
        TeamView homeTeam,
        TeamView awayTeam,
        Instant startTime,
        CoverageMode coverageMode
) {
}
