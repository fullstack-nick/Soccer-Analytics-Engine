package com.example.sportsanalytics.application.match.dto;

import com.example.sportsanalytics.domain.model.CoverageMode;
import java.util.List;
import java.util.UUID;

public record TrackMatchResult(
        UUID matchId,
        String providerMatchId,
        CoverageMode coverageMode,
        long stateVersion,
        TeamView homeTeam,
        TeamView awayTeam,
        int minute,
        int homeScore,
        int awayScore,
        int eventsInserted,
        int eventsUpdated,
        List<String> rawPayloadsFetched,
        List<String> rawPayloadsFromCache,
        List<String> skippedOptionalEndpoints
) {
    public TrackMatchResult {
        rawPayloadsFetched = List.copyOf(rawPayloadsFetched == null ? List.of() : rawPayloadsFetched);
        rawPayloadsFromCache = List.copyOf(rawPayloadsFromCache == null ? List.of() : rawPayloadsFromCache);
        skippedOptionalEndpoints = List.copyOf(skippedOptionalEndpoints == null ? List.of() : skippedOptionalEndpoints);
    }
}
